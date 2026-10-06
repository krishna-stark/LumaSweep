package com.example.photostorage.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class Yolo11SemanticAnalyzer(context: Context) : AutoCloseable {
    private val interpreter: Interpreter
    private val inputWidth: Int
    private val inputHeight: Int

    init {
        val descriptor = context.assets.openFd(MODEL_FILE)
        val model = descriptor.createInputStream().channel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            descriptor.startOffset,
            descriptor.declaredLength,
        )
        descriptor.close()
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })
        val shape = interpreter.getInputTensor(0).shape()
        inputHeight = shape[1]
        inputWidth = shape[2]
    }

    fun labels(bitmap: Bitmap): Set<String> = runCatching {
        val inputTensor = interpreter.getInputTensor(0)
        val input = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())
        val prepared = letterbox(bitmap)
        val pixels = IntArray(inputWidth * inputHeight)
        prepared.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val quantization = inputTensor.quantizationParams()
        pixels.forEach { color ->
            val channels = floatArrayOf(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
            )
            channels.forEach { value -> putValue(input, inputTensor.dataType(), value, quantization.scale, quantization.zeroPoint) }
        }
        prepared.recycle()
        input.rewind()

        val outputTensor = interpreter.getOutputTensor(0)
        val output = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        parseOutput(output, outputTensor.shape(), outputTensor.dataType(), outputTensor.quantizationParams().scale, outputTensor.quantizationParams().zeroPoint)
    }.getOrDefault(emptySet())

    private fun parseOutput(
        output: ByteBuffer,
        shape: IntArray,
        type: DataType,
        scale: Float,
        zeroPoint: Int,
    ): Set<String> {
        if (shape.size != 3) return emptySet()
        val count = shape.drop(1).reduce(Int::times)
        val values = FloatArray(count) { readValue(output, type, scale, zeroPoint) }
        val featuresFirst = shape[1] < shape[2]
        val features = if (featuresFirst) shape[1] else shape[2]
        val candidates = if (featuresFirst) shape[2] else shape[1]
        if (features < 5) return emptySet()
        val classCount = minOf(COCO_LABELS.size, features - 4)
        val found = linkedSetOf<String>()
        for (candidate in 0 until candidates) {
            var bestClass = -1
            var bestScore = 0f
            for (classIndex in 0 until classCount) {
                val feature = classIndex + 4
                val index = if (featuresFirst) feature * candidates + candidate else candidate * features + feature
                val score = values[index]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = classIndex
                }
            }
            if (bestClass >= 0 && bestScore >= CONFIDENCE_THRESHOLD) found += COCO_LABELS[bestClass]
        }
        return found
    }

    private fun letterbox(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val scale = minOf(inputWidth.toFloat() / source.width, inputHeight.toFloat() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF((inputWidth - width) / 2, (inputHeight - height) / 2, (inputWidth + width) / 2, (inputHeight + height) / 2),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return output
    }

    private fun putValue(buffer: ByteBuffer, type: DataType, value: Float, scale: Float, zeroPoint: Int) {
        when (type) {
            DataType.FLOAT32 -> buffer.putFloat(value)
            DataType.UINT8 -> buffer.put(((value / scale + zeroPoint).roundToInt().coerceIn(0, 255)).toByte())
            DataType.INT8 -> buffer.put(((value / scale + zeroPoint).roundToInt().coerceIn(-128, 127)).toByte())
            else -> error("Unsupported YOLO input type: $type")
        }
    }

    private fun readValue(buffer: ByteBuffer, type: DataType, scale: Float, zeroPoint: Int): Float = when (type) {
        DataType.FLOAT32 -> buffer.float
        DataType.UINT8 -> ((buffer.get().toInt() and 0xFF) - zeroPoint) * scale
        DataType.INT8 -> (buffer.get().toInt() - zeroPoint) * scale
        else -> error("Unsupported YOLO output type: $type")
    }

    override fun close() = interpreter.close()

    companion object {
        private const val MODEL_FILE = "yolo11n_int8.tflite"
        private const val CONFIDENCE_THRESHOLD = .35f
        val COCO_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed",
            "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven",
            "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush",
        )
    }
}
