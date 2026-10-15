package com.dbpsecurity.dbpcheckin.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.InterpreterApi
import com.google.android.gms.tflite.java.TfLite
import com.google.android.gms.tasks.Tasks
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

// Uses Play Services TFLite (Thin Client) to avoid 16KB alignment issues
class FaceRecognitionHelper(private val context: Context) {

    private var interpreter: InterpreterApi? = null
    private var initError: Throwable? = null
    private val inputImageSize = 160 // FaceNet standard input size
    private val outputEmbeddingSize = 128 // FaceNet standard output size

    fun getFaceEmbedding(bitmap: Bitmap): FloatArray {
        if (interpreter == null) {
            try {
                // Initialize Play Services TFLite (Async, so we wait)
                val initializeTask = TfLite.initialize(context)
                Tasks.await(initializeTask) // Blocking wait on background thread

                // Loading model directly
                val assetFileDescriptor = context.assets.openFd("facenet.tflite")
                val inputStream = java.io.FileInputStream(assetFileDescriptor.fileDescriptor)
                val fileChannel = inputStream.channel
                val startOffset = assetFileDescriptor.startOffset
                val declaredLength = assetFileDescriptor.declaredLength
                val modelByteBuffer = fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

                val options = InterpreterApi.Options()
                options.setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
                interpreter = InterpreterApi.create(modelByteBuffer, options)
            } catch (e: Throwable) {
                initError = e
                e.printStackTrace()
                throw IllegalStateException("Failed to initialize Play Services TFLite: ${e.message}", e)
            }
        }

        if (interpreter == null) {
             throw IllegalStateException("TFLite Interpreter is not initialized. Error: ${initError?.message}", initError)
        }

        // 1. Resize Bitmap manually
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputImageSize, inputImageSize, true)

        // 2. Convert to ByteBuffer and Normalize
        val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // 3. Run Inference
        // The model expects [1, 160, 160, 3] input and outputs [1, 128]
        val outputArray = Array(1) { FloatArray(outputEmbeddingSize) }
        interpreter?.run(inputBuffer, outputArray)

        val normalized = l2Normalize(outputArray[0])
        android.util.Log.d("FaceRecognitionHelper", "Embedding: ${normalized.take(5).joinToString(", ")}...")
        return normalized
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputImageSize * inputImageSize * 3 * 4) // 4 bytes per float
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputImageSize * inputImageSize)
        bitmap.getPixels(intValues, 0, inputImageSize, 0, 0, inputImageSize, inputImageSize)

        // Normalize to [-1, 1] -> (value - 127.5) / 127.5
        var pixel = 0
        for (i in 0 until inputImageSize) {
            for (j in 0 until inputImageSize) {
                val input = intValues[pixel++]

                val r = (input shr 16 and 0xFF)
                val g = (input shr 8 and 0xFF)
                val b = (input and 0xFF)

                byteBuffer.putFloat((r - 127.5f) / 127.5f)
                byteBuffer.putFloat((g - 127.5f) / 127.5f)
                byteBuffer.putFloat((b - 127.5f) / 127.5f)
            }
        }
        return byteBuffer
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0.0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        if (norm == 0f) return embedding // Avoid division by zero

        return FloatArray(embedding.size) { i -> embedding[i] / norm }
    }

    fun calculateDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        var sum = 0.0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    fun close() {
        interpreter?.close()
    }

    companion object {
        fun preWarm(context: Context) {
            try {
                // Pre-initialize Play Services TFLite to reduce latency on first use
                // This downloads/updates the module if needed
                val initializeTask = TfLite.initialize(context)
                Tasks.await(initializeTask)
            } catch (e: Exception) {
                // Ignore errors here; they will be caught again in the actual usage
                e.printStackTrace()
            }
        }
    }
}
