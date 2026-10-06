package com.example.photostorage.data

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OnDeviceOcrAnalyzer {
    data class Snapshot(val text: String, val density: Float)

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyze(bitmap: Bitmap): Snapshot = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val imageArea = (bitmap.width.toLong() * bitmap.height).coerceAtLeast(1)
                val coveredArea = result.textBlocks.sumOf { block ->
                    block.boundingBox?.let { it.width().toLong() * it.height() } ?: 0L
                }
                if (continuation.isActive) continuation.resume(Snapshot(
                    text = result.text,
                    density = (coveredArea.toFloat() / imageArea).coerceIn(0f, 1f),
                ))
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(Snapshot("", 0f))
            }
    }

    suspend fun textDensity(bitmap: Bitmap): Float = analyze(bitmap).density
}
