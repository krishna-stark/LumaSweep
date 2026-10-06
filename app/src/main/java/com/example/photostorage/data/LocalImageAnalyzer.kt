package com.example.photostorage.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.photostorage.domain.OptimizationMode
import com.example.photostorage.domain.OptimizationPolicy
import com.example.photostorage.domain.OptimizationSettings
import com.example.photostorage.domain.OptimizationStatus
import com.example.photostorage.domain.PhotoRecord
import com.example.photostorage.domain.ResolvedOptimizationPolicy
import com.example.photostorage.domain.ResolvedOutputFormat
import com.example.photostorage.domain.ScreenshotCategory
import com.example.photostorage.domain.ScreenshotClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class LocalImageAnalyzer(context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val ocrAnalyzer by lazy { OnDeviceOcrAnalyzer() }
    private val semanticAnalyzer by lazy { Yolo11SemanticAnalyzer(context) }
    data class Result(
        val perceptualHash: String?,
        val blurScore: Float?,
        val screenshot: Boolean,
        val optimizedBytes: Long,
        val optimizedWidth: Int,
        val optimizedHeight: Int,
        val textDensity: Float?,
        val semanticLabels: Set<String>,
        val protectionReason: String?,
        val optimizationQuality: Int,
        val optimizationSimilarity: Float,
        val optimizationMode: OptimizationMode,
        val optimizedMimeType: String,
        val optimizedExtension: String,
        val screenshotCategory: ScreenshotCategory,
    )

    suspend fun analyze(photo: PhotoRecord): Result = withContext(Dispatchers.IO) {
        val screenshot = isScreenshot(photo)
        val analysisBitmap = decodeScaled(photo, ANALYSIS_EDGE)
        val hash = analysisBitmap?.let(::differenceHash)
        val blur = analysisBitmap?.let(::varianceOfLaplacian)
        val initialProtection = cheapProtectionReason(photo, screenshot)
        val potentialOptimization = initialProtection == null && isAnyOptimizationCandidate(photo, screenshot)
        analysisBitmap?.recycle()
        val estimate = if (potentialOptimization) estimateForScan(photo) else null

        Result(
            perceptualHash = hash,
            blurScore = blur,
            screenshot = screenshot,
            optimizedBytes = estimate?.bytes ?: 0,
            optimizedWidth = estimate?.width ?: 0,
            optimizedHeight = estimate?.height ?: 0,
            textDensity = null,
            semanticLabels = emptySet(),
            protectionReason = initialProtection,
            optimizationQuality = 0,
            optimizationSimilarity = 0f,
            optimizationMode = estimate?.mode ?: OptimizationMode.NONE,
            optimizedMimeType = estimate?.mimeType.orEmpty(),
            optimizedExtension = estimate?.extension.orEmpty(),
            screenshotCategory = ScreenshotClassifier.classify(photo.relativePath, photo.displayName),
        )
    }

    data class OptimizationEstimate(
        val bytes: Long,
        val width: Int,
        val height: Int,
        val mode: OptimizationMode,
        val mimeType: String,
        val extension: String,
    )

    fun estimateForScan(
        photo: PhotoRecord,
        settings: OptimizationSettings = OptimizationSettings(),
    ): OptimizationEstimate? {
        val screenshot = isScreenshot(photo)
        if (cheapProtectionReason(photo, screenshot) != null || !isAnyOptimizationCandidate(photo, screenshot)) return null
        if (isLosslessCandidate(photo, screenshot)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val ratio = when {
                photo.mimeType.equals("image/png", true) -> ESTIMATED_PNG_TO_WEBP_RATIO
                photo.mimeType.equals("image/webp", true) -> ESTIMATED_WEBP_REENCODE_RATIO
                else -> ESTIMATED_SCREENSHOT_JPEG_TO_WEBP_RATIO
            }
            val estimatedBytes = (photo.fileSize * ratio).toLong().coerceAtLeast(1)
            val saving = photo.fileSize - estimatedBytes
            return OptimizationEstimate(
                bytes = estimatedBytes,
                width = photo.width,
                height = photo.height,
                mode = OptimizationMode.LOSSLESS_SCREENSHOT,
                mimeType = WEBP_MIME_TYPE,
                extension = WEBP_EXTENSION,
            ).takeIf {
                saving >= MIN_LOSSLESS_SAVING_BYTES && estimatedBytes <= (photo.fileSize * MAX_LOSSLESS_OUTPUT_RATIO).toLong()
            }
        }
        val policy = OptimizationPolicy.resolve(settings, photo.mimeType, photo.width, photo.height)
        val longEdge = max(photo.width, photo.height).coerceAtLeast(1)
        val scale = min(1f, policy.longEdge.toFloat() / longEdge)
        val width = (photo.width * scale).roundToInt().coerceAtLeast(1)
        val height = (photo.height * scale).roundToInt().coerceAtLeast(1)
        val pixelRatio = (width.toDouble() * height) / (photo.width.toDouble() * photo.height.coerceAtLeast(1))
        val estimatedBytes = (photo.fileSize * pixelRatio * ESTIMATED_ENCODING_FACTOR).toLong()
            .coerceAtLeast(1L)
        val saving = photo.fileSize - estimatedBytes
        return OptimizationEstimate(
            estimatedBytes,
            width,
            height,
            OptimizationMode.PHOTO_DOWNSIZE,
            JPEG_MIME_TYPE,
            JPEG_EXTENSION,
        ).takeIf {
            saving >= MIN_SAVING_BYTES && estimatedBytes <= (photo.fileSize * MAX_ESTIMATED_OUTPUT_RATIO).toLong()
        }
    }

    suspend fun encodeCandidate(
        photo: PhotoRecord,
        verifiedCandidate: EncodedPhoto? = null,
        settings: OptimizationSettings = OptimizationSettings(),
    ): EncodedPhoto? = withContext(Dispatchers.IO) {
        if (!sourceStillMatches(photo)) return@withContext null
        val screenshot = isScreenshot(photo)
        if (isLosslessCandidate(photo, screenshot)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null
            if (verifiedCandidate?.mode == OptimizationMode.LOSSLESS_SCREENSHOT &&
                verifiedCandidate.settingsFingerprint == settings.fingerprint()
            ) return@withContext verifiedCandidate
            return@withContext encodeLosslessCandidate(photo, settings)
        }
        val analysisBitmap = decodeScaled(photo, ANALYSIS_EDGE) ?: return@withContext null
        val metadataRisk = readMetadataRisk(photo)
        val protection = protectionReason(photo, screenshot, analysisBitmap, metadataRisk)
        val ocr = ocrAnalyzer.analyze(analysisBitmap)
        val textDensity = ocr.density
        val semanticLabels = semanticAnalyzer.labels(analysisBitmap)
        analysisBitmap.recycle()
        val hasPersonalContent = semanticLabels.any { it in PERSONAL_CONTENT_LABELS }
        val contentProtection = when {
            textDensity >= MAX_TEXT_DENSITY -> "Contains important text"
            semanticLabels.any { it in TEXT_SENSITIVE_LABELS } -> "Contains a screen or document"
            else -> null
        }
        if (protection != null || contentProtection != null || !isPhotoOptimizationCandidate(photo)) {
            return@withContext null
        }
        val policy = OptimizationPolicy.resolve(settings, photo.mimeType, photo.width, photo.height)
        if (verifiedCandidate?.settingsFingerprint == policy.settingsFingerprint) return@withContext verifiedCandidate
        val reference = decodeScaled(photo, policy.longEdge) ?: return@withContext null
        try {
            for (quality in policy.minimumQuality..policy.maximumQuality) {
                val output = ByteArrayOutputStream()
                if (!reference.compress(compressFormat(policy.outputFormat), quality, output)) continue
                val bytes = output.toByteArray()
                val saving = photo.fileSize - bytes.size
                if (saving < policy.minimumSavingBytes || bytes.size > (photo.fileSize * policy.maximumOutputRatio).toLong()) {
                    return@withContext null
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                val qualityResult = compareQuality(reference, decoded)
                decoded.recycle()
                // Opening this screen is the individual review required for people and pets.
                // They may still be offered a copy, but only at the stricter quality tier.
                if (qualityResult.passed(hasPersonalContent, policy)) {
                    return@withContext EncodedPhoto(
                        bytes = bytes,
                        width = reference.width,
                        height = reference.height,
                        quality = quality,
                        similarity = qualityResult.globalSimilarity,
                        requiresIndividualReview = hasPersonalContent,
                        mode = OptimizationMode.PHOTO_DOWNSIZE,
                        mimeType = policy.outputFormat.mimeType,
                        extension = policy.outputFormat.extension,
                        settingsFingerprint = policy.settingsFingerprint,
                    )
                }
            }
            return@withContext null
        } finally {
            reference.recycle()
        }
    }

    /** Recreates bytes from a persisted verification proof without repeating OCR or semantic analysis. */
    suspend fun materializeVerifiedCandidate(
        photo: PhotoRecord,
        settings: OptimizationSettings = OptimizationSettings(),
    ): EncodedPhoto? = withContext(Dispatchers.IO) {
        if (photo.optimizationStatus != OptimizationStatus.VERIFIED ||
            photo.optimizationSettingsFingerprint != settings.fingerprint() ||
            !sourceStillMatches(photo)
        ) return@withContext null

        val longEdge = max(photo.optimizedWidth, photo.optimizedHeight).coerceAtLeast(1)
        val reference = decodeScaled(photo, longEdge) ?: return@withContext null
        try {
            val output = ByteArrayOutputStream()
            val compressed = when (photo.optimizationMode) {
                OptimizationMode.LOSSLESS_SCREENSHOT -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) false
                    else compressPersistedLossless(reference, output)
                }
                OptimizationMode.PHOTO_DOWNSIZE -> reference.compress(
                    Bitmap.CompressFormat.JPEG,
                    photo.optimizationQuality.coerceIn(1, 100),
                    output,
                )
                OptimizationMode.NONE -> false
            }
            if (!compressed) return@withContext null
            val bytes = output.toByteArray()
            if (bytes.isEmpty() || bytes.size >= photo.fileSize) return@withContext null
            EncodedPhoto(
                bytes = bytes,
                width = reference.width,
                height = reference.height,
                quality = photo.optimizationQuality,
                similarity = photo.optimizationSimilarity,
                requiresIndividualReview = photo.optimizationRequiresReview,
                mode = photo.optimizationMode,
                mimeType = photo.optimizedMimeType,
                extension = photo.optimizedExtension,
                screenshotCategory = photo.screenshotCategory,
                settingsFingerprint = photo.optimizationSettingsFingerprint,
            )
        } finally {
            reference.recycle()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun compressPersistedLossless(bitmap: Bitmap, output: ByteArrayOutputStream): Boolean =
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)

    data class EncodedPhoto(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val quality: Int,
        val similarity: Float,
        val requiresIndividualReview: Boolean = false,
        val mode: OptimizationMode = OptimizationMode.PHOTO_DOWNSIZE,
        val mimeType: String = JPEG_MIME_TYPE,
        val extension: String = JPEG_EXTENSION,
        val screenshotCategory: ScreenshotCategory = ScreenshotCategory.OTHER,
        val settingsFingerprint: String = OptimizationSettings().fingerprint(),
    ) {
        val size: Int get() = bytes.size
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun encodeLosslessCandidate(
        photo: PhotoRecord,
        settings: OptimizationSettings,
    ): EncodedPhoto? {
        val reference = decodeScaled(photo, max(photo.width, photo.height).coerceAtLeast(1)) ?: return null
        return try {
            val beforeOcr = ocrAnalyzer.analyze(reference)
            val output = ByteArrayOutputStream()
            if (!reference.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)) return null
            val bytes = output.toByteArray()
            val saving = photo.fileSize - bytes.size
            if (saving < MIN_LOSSLESS_SAVING_BYTES || bytes.size > photo.fileSize * MAX_LOSSLESS_OUTPUT_RATIO) return null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            try {
                if (decoded.width != reference.width || decoded.height != reference.height || !reference.sameAs(decoded)) return null
                val afterOcr = ocrAnalyzer.analyze(decoded)
                if (normalizeRecognizedText(beforeOcr.text) != normalizeRecognizedText(afterOcr.text)) return null
                EncodedPhoto(
                    bytes = bytes,
                    width = reference.width,
                    height = reference.height,
                    quality = 100,
                    similarity = 1f,
                    mode = OptimizationMode.LOSSLESS_SCREENSHOT,
                    mimeType = WEBP_MIME_TYPE,
                    extension = WEBP_EXTENSION,
                    screenshotCategory = ScreenshotClassifier.classify(
                        photo.relativePath,
                        photo.displayName,
                        beforeOcr.text,
                    ),
                    settingsFingerprint = settings.fingerprint(),
                )
            } finally {
                decoded.recycle()
            }
        } finally {
            reference.recycle()
        }
    }

    private fun normalizeRecognizedText(value: String): String =
        value.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun isAnyOptimizationCandidate(photo: PhotoRecord, screenshot: Boolean): Boolean =
        isLosslessCandidate(photo, screenshot) || isPhotoOptimizationCandidate(photo)

    private fun isLosslessCandidate(photo: PhotoRecord, screenshot: Boolean): Boolean =
        photo.fileSize >= MIN_LOSSLESS_ORIGINAL_BYTES &&
            (screenshot || photo.mimeType.equals("image/png", true) || photo.mimeType.equals("image/webp", true))

    private fun isPhotoOptimizationCandidate(photo: PhotoRecord): Boolean =
        photo.mimeType.equals("image/jpeg", ignoreCase = true) &&
            photo.width.toLong() * photo.height >= MIN_PIXELS &&
            photo.fileSize >= MIN_ORIGINAL_BYTES &&
            max(photo.width, photo.height) > OPTIMIZED_LONG_EDGE

    private fun cheapProtectionReason(photo: PhotoRecord, screenshot: Boolean): String? = when {
        isLosslessCandidate(photo, screenshot) -> null
        photo.isFavorite -> "Marked as a favorite"
        isRecent(photo) -> "Captured in the last 90 days"
        isBurst(photo) -> "Burst photo"
        isPanorama(photo) -> "Panorama"
        !photo.mimeType.equals("image/jpeg", ignoreCase = true) -> "Special image format"
        else -> null
    }

    private fun sourceStillMatches(photo: PhotoRecord): Boolean = runCatching {
        val projection = buildList {
            add(android.provider.MediaStore.Images.Media.SIZE)
            add(android.provider.MediaStore.Images.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(android.provider.MediaStore.Images.Media.IS_FAVORITE)
            }
        }.toTypedArray()
        resolver.query(photo.uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            val sizeMatches = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.SIZE)) == photo.fileSize
            val modifiedMatches = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_MODIFIED)) == photo.dateModified
            val favoriteMatches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                (cursor.getInt(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.IS_FAVORITE)) != 0) == photo.isFavorite
            } else true
            sizeMatches && modifiedMatches && favoriteMatches
        } ?: false
    }.getOrDefault(false)

    private fun protectionReason(
        photo: PhotoRecord,
        screenshot: Boolean,
        bitmap: Bitmap?,
        metadata: MetadataRisk,
    ): String? = when {
        photo.isFavorite -> "Marked as a favorite"
        isRecent(photo) -> "Captured in the last 90 days"
        screenshot -> "Screenshot"
        metadata.ultraHdr || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap?.hasGainmap() == true) -> "Ultra HDR photo"
        metadata.motionPhoto -> "Motion photo"
        metadata.edited -> "Edited or exported photo"
        isBurst(photo) -> "Burst photo"
        isPanorama(photo) -> "Panorama"
        bitmap?.colorSpace?.isWideGamut == true -> "Wide-color photo"
        !photo.mimeType.equals("image/jpeg", ignoreCase = true) -> "Special image format"
        else -> null
    }

    private fun isRecent(photo: PhotoRecord): Boolean {
        val modifiedMillis = photo.dateModified.takeIf { it > 0 }?.times(1000L) ?: 0L
        val capturedAt = max(photo.dateTaken, modifiedMillis)
        return capturedAt > 0 && System.currentTimeMillis() - capturedAt < RECENT_WINDOW_MILLIS
    }

    private fun isBurst(photo: PhotoRecord): Boolean =
        "${photo.relativePath}/${photo.displayName}".lowercase().let {
            it.contains("burst") || it.contains("continuous")
        }

    private fun isPanorama(photo: PhotoRecord): Boolean {
        val shortEdge = min(photo.width, photo.height).coerceAtLeast(1)
        return max(photo.width, photo.height).toFloat() / shortEdge > PANORAMA_ASPECT_RATIO
    }

    private data class MetadataRisk(val ultraHdr: Boolean, val motionPhoto: Boolean, val edited: Boolean)

    private fun readMetadataRisk(photo: PhotoRecord): MetadataRisk = runCatching {
        val exif = resolver.openInputStream(photo.uri)?.use(::ExifInterface)
            ?: return@runCatching MetadataRisk(false, false, false)
        val xmp = exif.getAttribute(ExifInterface.TAG_XMP).orEmpty().lowercase()
        val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty().lowercase()
        MetadataRisk(
            ultraHdr = xmp.contains("hdr-gain-map") || xmp.contains("hdrgm:") || xmp.contains("gainmap"),
            motionPhoto = xmp.contains("motionphoto") || xmp.contains("microvideo"),
            edited = EDITOR_MARKERS.any(software::contains),
        )
    }.getOrDefault(MetadataRisk(false, false, false))

    private data class QualityResult(
        val globalSimilarity: Float,
        val minimumTileSimilarity: Float,
        val edgeSimilarity: Float,
    ) {
        fun passed(personalContent: Boolean, policy: ResolvedOptimizationPolicy): Boolean {
            val globalThreshold = if (personalContent) policy.minimumPersonalGlobalSimilarity else policy.minimumGlobalSimilarity
            val tileThreshold = if (personalContent) policy.minimumPersonalTileSimilarity else policy.minimumTileSimilarity
            val edgeThreshold = if (personalContent) policy.minimumPersonalEdgeSimilarity else policy.minimumEdgeSimilarity
            return globalSimilarity >= globalThreshold &&
                minimumTileSimilarity >= tileThreshold &&
                edgeSimilarity >= edgeThreshold
        }
    }

    private fun compareQuality(reference: Bitmap, candidate: Bitmap): QualityResult {
        val referenceSample = scaledCopy(reference, QUALITY_EDGE)
        val candidateSample = scaledCopy(candidate, QUALITY_EDGE)
        return try {
            val global = structuralSimilarity(referenceSample, candidateSample, 0, 0, referenceSample.width, referenceSample.height)
            val tileWidth = (referenceSample.width / QUALITY_TILE_COUNT).coerceAtLeast(1)
            val tileHeight = (referenceSample.height / QUALITY_TILE_COUNT).coerceAtLeast(1)
            var minimumTile = 1f
            for (tileY in 0 until QUALITY_TILE_COUNT) {
                for (tileX in 0 until QUALITY_TILE_COUNT) {
                    val left = tileX * tileWidth
                    val top = tileY * tileHeight
                    val width = if (tileX == QUALITY_TILE_COUNT - 1) referenceSample.width - left else tileWidth
                    val height = if (tileY == QUALITY_TILE_COUNT - 1) referenceSample.height - top else tileHeight
                    minimumTile = min(minimumTile, structuralSimilarity(referenceSample, candidateSample, left, top, width, height))
                }
            }
            QualityResult(global, minimumTile, edgeCorrelation(referenceSample, candidateSample))
        } finally {
            if (referenceSample !== reference) referenceSample.recycle()
            if (candidateSample !== candidate) candidateSample.recycle()
        }
    }

    private fun scaledCopy(source: Bitmap, longEdge: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= longEdge) return source
        val scale = longEdge.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun structuralSimilarity(
        first: Bitmap,
        second: Bitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): Float {
        val count = width * height
        if (count <= 1) return 1f
        val firstPixels = IntArray(count)
        val secondPixels = IntArray(count)
        first.getPixels(firstPixels, 0, width, left, top, width, height)
        second.getPixels(secondPixels, 0, width, left, top, width, height)
        var firstMean = 0.0
        var secondMean = 0.0
        for (index in 0 until count) {
            firstMean += luminance(firstPixels[index])
            secondMean += luminance(secondPixels[index])
        }
        firstMean /= count
        secondMean /= count
        var firstVariance = 0.0
        var secondVariance = 0.0
        var covariance = 0.0
        for (index in 0 until count) {
            val firstDelta = luminance(firstPixels[index]) - firstMean
            val secondDelta = luminance(secondPixels[index]) - secondMean
            firstVariance += firstDelta * firstDelta
            secondVariance += secondDelta * secondDelta
            covariance += firstDelta * secondDelta
        }
        firstVariance /= count - 1
        secondVariance /= count - 1
        covariance /= count - 1
        val numerator = (2 * firstMean * secondMean + SSIM_C1) * (2 * covariance + SSIM_C2)
        val denominator = (firstMean * firstMean + secondMean * secondMean + SSIM_C1) *
            (firstVariance + secondVariance + SSIM_C2)
        return (numerator / denominator).toFloat().coerceIn(-1f, 1f)
    }

    private fun isScreenshot(photo: PhotoRecord): Boolean {
        val value = "${photo.relativePath}/${photo.displayName}".lowercase()
        return value.contains("screenshot") ||
            value.contains("screen_shot") ||
            value.contains("screen-shot") ||
            value.contains("screenrecord")
    }

    private fun decodeScaled(photo: PhotoRecord, longEdge: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return@runCatching ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, photo.uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val sourceWidth = info.size.width
                val sourceHeight = info.size.height
                val largest = max(sourceWidth, sourceHeight)
                if (largest > longEdge) {
                    val scale = longEdge.toFloat() / largest
                    decoder.setTargetSize(
                        (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                        (sourceHeight * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth / (sample * 2), bounds.outHeight / (sample * 2)) >= longEdge) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(photo.uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val largest = max(decoded.width, decoded.height)
        if (largest <= longEdge) return decoded
        val scale = longEdge.toFloat() / largest
        val resized = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (resized !== decoded) decoded.recycle()
        resized
    }.getOrNull()

    private fun differenceHash(source: Bitmap): String {
        val bitmap = Bitmap.createScaledBitmap(source, 9, 8, true)
        var hash = 0uL
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(bitmap.getPixel(x, y))
                val right = luminance(bitmap.getPixel(x + 1, y))
                if (left > right) hash = hash or (1uL shl bit)
                bit++
            }
        }
        bitmap.recycle()
        return hash.toString(16).padStart(16, '0')
    }

    private fun varianceOfLaplacian(source: Bitmap): Float {
        val bitmap = Bitmap.createScaledBitmap(source, 128, 128, true)
        val width = bitmap.width
        val height = bitmap.height
        val gray = IntArray(width * height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        pixels.indices.forEach { gray[it] = luminance(pixels[it]) }
        var sum = 0.0
        var sumSquared = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val laplacian = 4 * gray[index] - gray[index - 1] - gray[index + 1] -
                    gray[index - width] - gray[index + width]
                sum += laplacian
                sumSquared += laplacian.toDouble() * laplacian
                count++
            }
        }
        bitmap.recycle()
        if (count == 0) return Float.MAX_VALUE
        val mean = sum / count
        return (sumSquared / count - mean * mean).toFloat()
    }

    /**
     * Correlates edge locations instead of comparing raw Laplacian energy. JPEG encoding
     * normally changes edge energy slightly even when the same detail remains visible, so an
     * extremely narrow energy ratio rejected otherwise faithful copies. Correlation remains
     * strict about edges moving or disappearing while tolerating that harmless scale change.
     */
    private fun edgeCorrelation(first: Bitmap, second: Bitmap): Float {
        val firstBitmap = Bitmap.createScaledBitmap(first, EDGE_COMPARISON_SIZE, EDGE_COMPARISON_SIZE, true)
        val secondBitmap = Bitmap.createScaledBitmap(second, EDGE_COMPARISON_SIZE, EDGE_COMPARISON_SIZE, true)
        return try {
            val width = firstBitmap.width
            val height = firstBitmap.height
            val firstPixels = IntArray(width * height)
            val secondPixels = IntArray(width * height)
            firstBitmap.getPixels(firstPixels, 0, width, 0, 0, width, height)
            secondBitmap.getPixels(secondPixels, 0, width, 0, 0, width, height)
            var sumFirst = 0.0
            var sumSecond = 0.0
            var sumFirstSquared = 0.0
            var sumSecondSquared = 0.0
            var sumProduct = 0.0
            var count = 0
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val index = y * width + x
                    val firstEdge = 4 * luminance(firstPixels[index]) -
                        luminance(firstPixels[index - 1]) - luminance(firstPixels[index + 1]) -
                        luminance(firstPixels[index - width]) - luminance(firstPixels[index + width])
                    val secondEdge = 4 * luminance(secondPixels[index]) -
                        luminance(secondPixels[index - 1]) - luminance(secondPixels[index + 1]) -
                        luminance(secondPixels[index - width]) - luminance(secondPixels[index + width])
                    sumFirst += firstEdge
                    sumSecond += secondEdge
                    sumFirstSquared += firstEdge.toDouble() * firstEdge
                    sumSecondSquared += secondEdge.toDouble() * secondEdge
                    sumProduct += firstEdge.toDouble() * secondEdge
                    count++
                }
            }
            if (count < 2) return 1f
            val covariance = sumProduct - sumFirst * sumSecond / count
            val firstVariance = sumFirstSquared - sumFirst * sumFirst / count
            val secondVariance = sumSecondSquared - sumSecond * sumSecond / count
            val denominator = kotlin.math.sqrt((firstVariance * secondVariance).coerceAtLeast(0.0))
            if (denominator <= .0001) 1f else (covariance / denominator).toFloat().coerceIn(-1f, 1f)
        } finally {
            firstBitmap.recycle()
            secondBitmap.recycle()
        }
    }

    private fun luminance(color: Int): Int {
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    @Suppress("DEPRECATION")
    private fun compressFormat(format: ResolvedOutputFormat): Bitmap.CompressFormat = when (format) {
        ResolvedOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ResolvedOutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
    }

    companion object {
        const val ANALYSIS_VERSION = 6
        private const val ANALYSIS_EDGE = 256
        private const val QUALITY_EDGE = 512
        private const val QUALITY_TILE_COUNT = 4
        private const val OPTIMIZED_LONG_EDGE = 2560
        private const val MIN_JPEG_QUALITY = 90
        private const val MAX_JPEG_QUALITY = 98
        private const val MIN_PIXELS = 12_000_000L
        private const val MIN_ORIGINAL_BYTES = 4L * 1024 * 1024
        private const val MIN_SAVING_BYTES = 1L * 1024 * 1024
        private const val MIN_LOSSLESS_ORIGINAL_BYTES = 512L * 1024
        private const val MIN_LOSSLESS_SAVING_BYTES = 256L * 1024
        private const val MAX_OUTPUT_RATIO = .60
        // The scan list needs headroom because it is only an estimate. Actual validation still
        // accepts up to 60%, but estimated candidates must predict at least a 50% reduction.
        private const val MAX_ESTIMATED_OUTPUT_RATIO = .50
        // High-quality JPEG output is not reliably proportional to the source JPEG. The
        // conservative multiplier intentionally understates savings so the home list favors
        // candidates likely to survive the real encode and validation pass.
        private const val ESTIMATED_ENCODING_FACTOR = 1.15
        private const val ESTIMATED_PNG_TO_WEBP_RATIO = .55
        private const val ESTIMATED_SCREENSHOT_JPEG_TO_WEBP_RATIO = .70
        // Existing WebP is usually already efficient. Do not surface it from an optimistic scan
        // estimate; a false positive is worse than missing a marginal re-encode opportunity.
        private const val ESTIMATED_WEBP_REENCODE_RATIO = .90
        private const val MAX_LOSSLESS_OUTPUT_RATIO = .80
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val JPEG_EXTENSION = "jpg"
        const val WEBP_MIME_TYPE = "image/webp"
        const val WEBP_EXTENSION = "webp"
        private const val MAX_TEXT_DENSITY = .12f
        private const val MIN_GLOBAL_SSIM = .985f
        private const val MIN_TILE_SSIM = .95f
        private const val MIN_EDGE_SIMILARITY = .94f
        private const val MIN_PERSONAL_GLOBAL_SSIM = .99f
        private const val MIN_PERSONAL_TILE_SSIM = .97f
        private const val MIN_PERSONAL_EDGE_SIMILARITY = .96f
        private const val EDGE_COMPARISON_SIZE = 128
        private const val PANORAMA_ASPECT_RATIO = 2.2f
        private const val RECENT_WINDOW_MILLIS = 90L * 24 * 60 * 60 * 1000
        private const val SSIM_C1 = 6.5025
        private const val SSIM_C2 = 58.5225
        private val TEXT_SENSITIVE_LABELS = setOf("book", "laptop", "tv", "cell phone")
        private val PERSONAL_CONTENT_LABELS = setOf(
            "person", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "teddy bear",
        )
        private val EDITOR_MARKERS = setOf(
            "adobe", "lightroom", "photoshop", "snapseed", "vsco", "picsart", "instagram", "canva", "gimp", "affinity",
        )
    }
}
