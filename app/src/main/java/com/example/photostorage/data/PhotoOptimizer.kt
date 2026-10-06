package com.example.photostorage.data

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.photostorage.domain.MetadataPreference
import com.example.photostorage.domain.OptimizationSettings
import com.example.photostorage.domain.PhotoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoOptimizer(
    private val resolver: ContentResolver,
    private val analyzer: LocalImageAnalyzer,
) {
    data class OptimizationResult(
        val uri: Uri,
        val originalBytes: Long,
        val finalBytes: Long,
    ) {
        val savedBytes: Long get() = (originalBytes - finalBytes).coerceAtLeast(0)
    }

    suspend fun createVerifiedOptimizedCopy(
        photo: PhotoRecord,
        verifiedCandidate: LocalImageAnalyzer.EncodedPhoto? = null,
        settings: OptimizationSettings = OptimizationSettings(),
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val encoded = requireNotNull(
            verifiedCandidate
                ?: analyzer.materializeVerifiedCandidate(photo, settings)
                ?: analyzer.encodeCandidate(photo, settings = settings)
        ) {
            "This photo no longer meets the optimization threshold."
        }
        val decoded = BitmapFactory.decodeByteArray(encoded.bytes, 0, encoded.bytes.size)
            ?: error("The optimized photo could not be decoded.")
        check(decoded.width == encoded.width && decoded.height == encoded.height) {
            "Optimized dimensions could not be verified."
        }
        decoded.recycle()

        val extension = encoded.extension.ifBlank { LocalImageAnalyzer.JPEG_EXTENSION }
        val mimeType = encoded.mimeType.ifBlank { LocalImageAnalyzer.JPEG_MIME_TYPE }
        val displayName = photo.displayName.substringBeforeLast('.', photo.displayName) + "_optimized.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.DATE_TAKEN, photo.dateTaken)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, photo.relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val outputUri = resolver.insert(collection, values) ?: error("Could not create optimized photo.")
        try {
            resolver.openOutputStream(outputUri, "w")?.use { it.write(encoded.bytes) }
                ?: error("Could not write optimized photo.")
            copyExpectedMetadata(photo.uri, outputUri, encoded.width, encoded.height, settings.metadata)
            resolver.openInputStream(outputUri)?.use { input ->
                val verification = BitmapFactory.decodeStream(input)
                check(verification != null) { "Saved photo could not be read back." }
                verification.recycle()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(outputUri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
            val finalBytes = resolver.openFileDescriptor(outputUri, "r")?.use { descriptor ->
                descriptor.statSize.takeIf { it >= 0 }
            } ?: encoded.size.toLong()
            check(finalBytes < photo.fileSize) { "The saved copy did not reduce storage." }
            OptimizationResult(outputUri, photo.fileSize, finalBytes)
        } catch (error: Throwable) {
            resolver.delete(outputUri, null, null)
            throw error
        }
    }

    suspend fun removeOwnedCopy(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(uri, null, null) }
    }

    private fun copyExpectedMetadata(
        sourceUri: Uri,
        outputUri: Uri,
        width: Int,
        height: Int,
        metadata: MetadataPreference,
    ) {
        val source = resolver.openInputStream(sourceUri)?.use(::ExifInterface) ?: return
        resolver.openFileDescriptor(outputUri, "rw")?.use { descriptor ->
            val output = ExifInterface(descriptor.fileDescriptor)
            val tags = if (metadata == MetadataPreference.PRESERVE) PRESERVED_EXIF_TAGS else REQUIRED_DATE_TAGS
            tags.forEach { tag ->
                source.getAttribute(tag)?.let { output.setAttribute(tag, it) }
            }
            output.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            output.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width.toString())
            output.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height.toString())
            output.saveAttributes()
        }
    }

    private companion object {
        val REQUIRED_DATE_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        )
        val PRESERVED_EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
        )
    }
}
