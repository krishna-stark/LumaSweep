package com.example.photostorage.data

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.example.photostorage.domain.DuplicateGroup
import com.example.photostorage.domain.ExactDuplicateEngine
import com.example.photostorage.domain.PhotoRecord
import com.example.photostorage.domain.OptimizationMode
import com.example.photostorage.domain.OptimizationSettings
import com.example.photostorage.domain.OptimizationStatus
import com.example.photostorage.domain.ScreenshotCategory
import com.example.photostorage.domain.ScreenshotClassifier
import com.example.photostorage.domain.RefreshChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class MediaStorePhotoScanner(
    private val resolver: ContentResolver,
    private val dao: MediaItemDao,
    private val analyzer: LocalImageAnalyzer,
) {
    data class Progress(
        val totalPhotos: Int,
        val checkedPhotos: Int,
        val totalBytes: Long,
        val groups: List<DuplicateGroup>,
        val photos: List<PhotoRecord>,
        val phase: String,
        val refreshChanges: RefreshChanges = RefreshChanges(),
    )

    private data class Discovery(
        val photos: List<PhotoRecord>,
        val changes: RefreshChanges,
    )

    suspend fun cachedSnapshot(): Progress = withContext(Dispatchers.IO) {
        val photos = dao.getAll().map { it.toRecord() }.sortedByDescending { it.dateTaken }
        Progress(
            totalPhotos = photos.size,
            checkedPhotos = photos.size,
            totalBytes = photos.sumOf { it.fileSize },
            groups = ExactDuplicateEngine.duplicateGroups(photos),
            photos = photos,
            phase = if (photos.isEmpty()) "Ready to scan" else "Showing your last scan",
        )
    }

    suspend fun scan(onProgress: suspend (Progress) -> Unit): List<PhotoRecord> =
        withContext(Dispatchers.IO) {
            val version = System.currentTimeMillis()
            val cached = dao.getAll().associateBy { it.mediaStoreId }
            val discovery = readMediaStore(version, cached)
            val discovered = discovery.photos
            val totalBytes = discovered.sumOf { it.fileSize }

            // Persist the newly discovered library before expensive work starts. If the
            // process is interrupted, the next launch can immediately show this snapshot.
            dao.replaceScan(discovered.map { it.toEntity(version) }, version)

            onProgress(Progress(discovered.size, 0, totalBytes, emptyList(), discovered, "Checking for new photos", discovery.changes))

            val candidateIds = ExactDuplicateEngine.candidateGroups(discovered)
                .flatten()
                .mapTo(hashSetOf()) { it.id }
            var checked = discovered.count { it.id !in candidateIds }
            val hashed = discovered.toMutableList()

            onProgress(Progress(discovered.size, checked, totalBytes, emptyList(), hashed, "Verifying exact duplicates", discovery.changes))

            val hashBatch = ArrayList<MediaItemEntity>(PROGRESS_BATCH)

            discovered.forEachIndexed { index, photo ->
                coroutineContext.ensureActive()
                if (photo.id in candidateIds) {
                    val resolved = if (photo.sha256 != null) photo else photo.copy(sha256 = sha256(photo))
                    hashed[index] = resolved
                    checked++
                    if (resolved !== photo) hashBatch += resolved.toEntity(version)

                    if (checked % PROGRESS_BATCH == 0 || checked == discovered.size) {
                        if (hashBatch.isNotEmpty()) {
                            dao.upsertAll(hashBatch)
                            hashBatch.clear()
                        }
                        onProgress(
                            Progress(
                                totalPhotos = discovered.size,
                                checkedPhotos = checked,
                                totalBytes = totalBytes,
                                groups = ExactDuplicateEngine.duplicateGroups(hashed),
                                photos = hashed,
                                phase = "Verifying exact duplicates",
                                refreshChanges = discovery.changes,
                            )
                        )
                        yield()
                    }
                }
            }

            val analyzed = hashed.toMutableList()
            checked = 0
            val analysisBatch = ArrayList<MediaItemEntity>(PROGRESS_BATCH)
            hashed.forEachIndexed { index, photo ->
                coroutineContext.ensureActive()
                analyzed[index] = if (photo.analysisVersion == LocalImageAnalyzer.ANALYSIS_VERSION) {
                    photo
                } else {
                    val result = analyzer.analyze(photo)
                    photo.copy(
                        perceptualHash = result.perceptualHash,
                        blurScore = result.blurScore,
                        isScreenshot = result.screenshot,
                        optimizedBytes = result.optimizedBytes,
                        optimizedWidth = result.optimizedWidth,
                        optimizedHeight = result.optimizedHeight,
                        analysisVersion = LocalImageAnalyzer.ANALYSIS_VERSION,
                        textDensity = result.textDensity,
                        semanticLabels = result.semanticLabels,
                        protectionReason = result.protectionReason,
                        optimizationQuality = result.optimizationQuality,
                        optimizationSimilarity = result.optimizationSimilarity,
                        optimizationMode = result.optimizationMode,
                        optimizedMimeType = result.optimizedMimeType,
                        optimizedExtension = result.optimizedExtension,
                        screenshotCategory = result.screenshotCategory,
                        optimizationStatus = if (result.optimizedBytes > 0) {
                            OptimizationStatus.ESTIMATED
                        } else {
                            OptimizationStatus.REJECTED
                        },
                    )
                }
                if (analyzed[index] !== photo) analysisBatch += analyzed[index].toEntity(version)
                checked++
                if (checked % PROGRESS_BATCH == 0 || checked == hashed.size) {
                    if (analysisBatch.isNotEmpty()) {
                        dao.upsertAll(analysisBatch)
                        analysisBatch.clear()
                    }
                    onProgress(
                        Progress(
                            totalPhotos = hashed.size,
                            checkedPhotos = checked,
                            totalBytes = totalBytes,
                            groups = ExactDuplicateEngine.duplicateGroups(analyzed),
                            photos = analyzed,
                            phase = "Analyzing quality, text, and objects",
                            refreshChanges = discovery.changes,
                        )
                    )
                    yield()
                }
            }

            val settings = OptimizationSettings()
            val settingsFingerprint = settings.fingerprint()
            val pendingVerification = analyzed.withIndex().filter { (_, photo) ->
                photo.optimizationStatus == OptimizationStatus.ESTIMATED ||
                    (photo.optimizationStatus == OptimizationStatus.UNKNOWN && photo.optimizedBytes > 0) ||
                    (photo.optimizationStatus == OptimizationStatus.VERIFIED &&
                        photo.optimizationSettingsFingerprint != settingsFingerprint)
            }
            val verificationBatch = ArrayList<MediaItemEntity>(VERIFICATION_PROGRESS_BATCH)
            pendingVerification.forEachIndexed { pendingIndex, indexedPhoto ->
                coroutineContext.ensureActive()
                val index = indexedPhoto.index
                val photo = indexedPhoto.value
                analyzed[index] = photo.copy(optimizationStatus = OptimizationStatus.VERIFYING)
                val candidate = analyzer.encodeCandidate(photo, settings = settings)
                val resolved = if (candidate == null) {
                    photo.copy(
                        optimizedBytes = 0,
                        optimizedWidth = 0,
                        optimizedHeight = 0,
                        optimizationQuality = 0,
                        optimizationSimilarity = 0f,
                        optimizationMode = OptimizationMode.NONE,
                        optimizedMimeType = "",
                        optimizedExtension = "",
                        optimizationStatus = OptimizationStatus.REJECTED,
                        optimizationSettingsFingerprint = settingsFingerprint,
                        optimizationRequiresReview = false,
                    )
                } else {
                    photo.copy(
                        optimizedBytes = candidate.size.toLong(),
                        optimizedWidth = candidate.width,
                        optimizedHeight = candidate.height,
                        optimizationQuality = candidate.quality,
                        optimizationSimilarity = candidate.similarity,
                        optimizationMode = candidate.mode,
                        optimizedMimeType = candidate.mimeType,
                        optimizedExtension = candidate.extension,
                        screenshotCategory = candidate.screenshotCategory,
                        optimizationStatus = OptimizationStatus.VERIFIED,
                        optimizationSettingsFingerprint = candidate.settingsFingerprint,
                        optimizationRequiresReview = candidate.requiresIndividualReview,
                    )
                }
                analyzed[index] = resolved
                verificationBatch += resolved.toEntity(version)
                val completed = pendingIndex + 1
                if (completed % VERIFICATION_PROGRESS_BATCH == 0 || completed == pendingVerification.size) {
                    dao.upsertAll(verificationBatch)
                    verificationBatch.clear()
                    val progressCount = if (pendingVerification.isEmpty()) discovered.size else {
                        ((completed.toFloat() / pendingVerification.size) * discovered.size).toInt()
                    }
                    onProgress(
                        Progress(
                            totalPhotos = discovered.size,
                            checkedPhotos = progressCount,
                            totalBytes = totalBytes,
                            groups = ExactDuplicateEngine.duplicateGroups(analyzed),
                            photos = analyzed,
                            phase = "Verifying real space savings",
                            refreshChanges = discovery.changes,
                        )
                    )
                    yield()
                }
            }

            val entities = analyzed.map { it.toEntity(version) }
            dao.replaceScan(entities, version)
            onProgress(
                Progress(
                    discovered.size,
                    discovered.size,
                    totalBytes,
                    ExactDuplicateEngine.duplicateGroups(analyzed),
                    analyzed,
                    "Scan complete",
                    discovery.changes,
                )
            )
            analyzed
        }

    private fun readMediaStore(
        version: Long,
        cached: Map<Long, MediaItemEntity>,
    ): Discovery {
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Images.Media.IS_FAVORITE)
            }
        }.toTypedArray()

        val photos = ArrayList<PhotoRecord>()
        var newPhotos = 0
        var changedPhotos = 0
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            } else -1
            val favoriteColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val modified = cursor.getLong(modifiedColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val previous = cached[id]
                val favorite = favoriteColumn >= 0 && cursor.getInt(favoriteColumn) != 0
                val unchanged = previous != null &&
                    previous.fileSize == size &&
                    previous.dateModified == modified &&
                    previous.width == width &&
                    previous.height == height &&
                    previous.favorite == favorite
                if (previous == null) newPhotos++ else if (!unchanged) changedPhotos++
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val record = PhotoRecord(
                    id = id,
                    uri = uri,
                    displayName = cursor.getString(nameColumn).orEmpty(),
                    mimeType = cursor.getString(mimeColumn).orEmpty().ifBlank { "image/*" },
                    width = width,
                    height = height,
                    fileSize = size,
                    dateTaken = cursor.getLong(takenColumn),
                    dateModified = modified,
                    relativePath = if (pathColumn >= 0) cursor.getString(pathColumn).orEmpty() else "",
                    sha256 = previous?.sha256?.takeIf { unchanged },
                    perceptualHash = previous?.perceptualHash?.takeIf { unchanged },
                    blurScore = previous?.blurScore?.takeIf { unchanged },
                    isScreenshot = previous?.screenshot?.takeIf { unchanged } ?: false,
                    optimizedBytes = previous?.optimizedBytes?.takeIf { unchanged } ?: 0,
                    optimizedWidth = previous?.optimizedWidth?.takeIf { unchanged } ?: 0,
                    optimizedHeight = previous?.optimizedHeight?.takeIf { unchanged } ?: 0,
                    analysisVersion = previous?.analysisVersion?.takeIf { unchanged } ?: 0,
                    textDensity = previous?.ocrConfidence?.takeIf { unchanged },
                    semanticLabels = previous?.semanticLabelsCsv?.takeIf { unchanged }
                        ?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
                    isFavorite = favorite,
                    protectionReason = previous?.protectionReason?.takeIf { unchanged },
                    optimizationQuality = previous?.optimizationQuality?.takeIf { unchanged } ?: 0,
                    optimizationSimilarity = previous?.optimizationSimilarity?.takeIf { unchanged } ?: 0f,
                    optimizationMode = previous?.optimizationMode?.takeIf { unchanged }
                        ?.let { runCatching { OptimizationMode.valueOf(it) }.getOrNull() } ?: OptimizationMode.NONE,
                    optimizedMimeType = previous?.optimizedMimeType?.takeIf { unchanged }.orEmpty(),
                    optimizedExtension = previous?.optimizedExtension?.takeIf { unchanged }.orEmpty(),
                    screenshotCategory = previous?.screenshotCategory?.takeIf { unchanged }
                        ?.let { runCatching { ScreenshotCategory.valueOf(it) }.getOrNull() }
                        ?: ScreenshotClassifier.classify(
                            if (pathColumn >= 0) cursor.getString(pathColumn).orEmpty() else "",
                            cursor.getString(nameColumn).orEmpty(),
                        ),
                    optimizationStatus = previous?.optimizationStatus?.takeIf { unchanged }
                        ?.let { runCatching { OptimizationStatus.valueOf(it) }.getOrNull() }
                        ?: OptimizationStatus.UNKNOWN,
                    optimizationSettingsFingerprint = previous?.optimizationSettingsFingerprint
                        ?.takeIf { unchanged }.orEmpty(),
                    optimizationRequiresReview = previous?.optimizationRequiresReview?.takeIf { unchanged } ?: false,
                )
                val estimate = if (record.analysisVersion == LocalImageAnalyzer.ANALYSIS_VERSION) {
                    null
                } else {
                    analyzer.estimateForScan(record)
                }
                photos += if (record.analysisVersion != LocalImageAnalyzer.ANALYSIS_VERSION) {
                    record.copy(
                        optimizedBytes = estimate?.bytes ?: 0,
                        optimizedWidth = estimate?.width ?: 0,
                        optimizedHeight = estimate?.height ?: 0,
                        optimizationMode = estimate?.mode ?: OptimizationMode.NONE,
                        optimizedMimeType = estimate?.mimeType.orEmpty(),
                        optimizedExtension = estimate?.extension.orEmpty(),
                        optimizationStatus = if (estimate == null) OptimizationStatus.REJECTED else OptimizationStatus.ESTIMATED,
                    )
                } else record
            }
        }
        val currentIds = photos.asSequence().map { it.id }.toHashSet()
        return Discovery(
            photos = photos,
            changes = RefreshChanges(
                newPhotos = newPhotos,
                changedPhotos = changedPhotos,
                deletedPhotos = cached.keys.count { it !in currentIds },
            ),
        )
    }

    private fun sha256(photo: PhotoRecord): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(photo.uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun PhotoRecord.toEntity(version: Long) = MediaItemEntity().also {
        it.mediaStoreId = id
        it.uri = uri.toString()
        it.displayName = displayName
        it.mimeType = mimeType
        it.width = width
        it.height = height
        it.fileSize = fileSize
        it.dateTaken = dateTaken
        it.dateModified = dateModified
        it.relativePath = relativePath
        it.sha256 = sha256
        it.perceptualHash = perceptualHash
        it.blurScore = blurScore
        it.screenshot = isScreenshot
        it.optimizedBytes = optimizedBytes
        it.optimizedWidth = optimizedWidth
        it.optimizedHeight = optimizedHeight
        it.analysisVersion = analysisVersion
        it.ocrConfidence = textDensity
        it.semanticLabelsCsv = semanticLabels.sorted().joinToString(",")
        it.favorite = isFavorite
        it.protectionReason = protectionReason
        it.optimizationQuality = optimizationQuality
        it.optimizationSimilarity = optimizationSimilarity
        it.optimizationMode = optimizationMode.name
        it.optimizedMimeType = optimizedMimeType
        it.optimizedExtension = optimizedExtension
        it.screenshotCategory = screenshotCategory.name
        it.optimizationStatus = optimizationStatus.name
        it.optimizationSettingsFingerprint = optimizationSettingsFingerprint
        it.optimizationRequiresReview = optimizationRequiresReview
        it.scanVersion = version
    }

    private fun MediaItemEntity.toRecord() = PhotoRecord(
        id = mediaStoreId,
        uri = android.net.Uri.parse(uri),
        displayName = displayName,
        mimeType = mimeType,
        width = width,
        height = height,
        fileSize = fileSize,
        dateTaken = dateTaken,
        dateModified = dateModified,
        relativePath = relativePath,
        sha256 = sha256,
        perceptualHash = perceptualHash,
        blurScore = blurScore,
        isScreenshot = screenshot,
        optimizedBytes = optimizedBytes,
        optimizedWidth = optimizedWidth,
        optimizedHeight = optimizedHeight,
        analysisVersion = analysisVersion,
        textDensity = ocrConfidence,
        semanticLabels = semanticLabelsCsv.split(',').filter { it.isNotBlank() }.toSet(),
        isFavorite = favorite,
        protectionReason = protectionReason,
        optimizationQuality = optimizationQuality,
        optimizationSimilarity = optimizationSimilarity,
        optimizationMode = runCatching { OptimizationMode.valueOf(optimizationMode) }.getOrDefault(OptimizationMode.NONE),
        optimizedMimeType = optimizedMimeType,
        optimizedExtension = optimizedExtension,
        screenshotCategory = runCatching { ScreenshotCategory.valueOf(screenshotCategory) }.getOrDefault(ScreenshotCategory.OTHER),
        optimizationStatus = runCatching { OptimizationStatus.valueOf(optimizationStatus) }.getOrDefault(OptimizationStatus.UNKNOWN),
        optimizationSettingsFingerprint = optimizationSettingsFingerprint,
        optimizationRequiresReview = optimizationRequiresReview,
    )

    private companion object {
        const val PROGRESS_BATCH = 128
        const val VERIFICATION_PROGRESS_BATCH = 4
    }
}
