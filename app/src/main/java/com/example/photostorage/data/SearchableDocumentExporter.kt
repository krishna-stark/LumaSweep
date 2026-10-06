package com.example.photostorage.data

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.photostorage.domain.PhotoRecord
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

class SearchableDocumentExporter(private val resolver: ContentResolver) {
    data class Result(val uri: Uri, val pageCount: Int, val recognizedBlocks: Int)

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    suspend fun export(photos: List<PhotoRecord>): Result = withContext(Dispatchers.IO) {
        require(photos.isNotEmpty()) { "Select at least one document image." }
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Searchable PDF export requires Android 10 or newer."
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val document = PdfDocument()
        var recognizedBlocks = 0
        try {
            photos.forEachIndexed { index, photo ->
                val bitmap = decodeForDocument(photo.uri) ?: error("Could not read ${photo.displayName}.")
                try {
                    val recognized = recognize(recognizer, bitmap)
                    recognizedBlocks += recognized.textBlocks.size
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    )
                    val canvas = page.canvas

                    // Draw a text layer first, then cover it with the source image. PDF readers can
                    // still search/select the text while the visible page remains the original image.
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
                    recognized.textBlocks.forEach { block ->
                        val box = block.boundingBox ?: return@forEach
                        val lines = block.lines.ifEmpty { return@forEach }
                        lines.forEach { line ->
                            val lineBox = line.boundingBox ?: box
                            paint.textSize = lineBox.height().coerceAtLeast(8).toFloat()
                            canvas.drawText(line.text, lineBox.left.toFloat(), lineBox.bottom.toFloat(), paint)
                        }
                    }
                    canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }

            val name = "LumaSweep_${System.currentTimeMillis()}.pdf"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/LumaSweep")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("Could not create the PDF.")
            try {
                resolver.openOutputStream(uri, "w")?.use(document::writeTo)
                    ?: error("Could not write the PDF.")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                Result(uri, photos.size, recognizedBlocks)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } finally {
            document.close()
            recognizer.close()
        }
    }

    private fun decodeForDocument(uri: Uri): Bitmap? = runCatching {
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = maxOf(info.size.width, info.size.height)
                if (longEdge > MAX_DOCUMENT_EDGE) {
                    val scale = MAX_DOCUMENT_EDGE.toFloat() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
        decoded ?: return@runCatching null
    }.getOrNull()

    private suspend fun recognize(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
    ): com.google.mlkit.vision.text.Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }

    private companion object {
        const val MAX_DOCUMENT_EDGE = 2400
    }
}
