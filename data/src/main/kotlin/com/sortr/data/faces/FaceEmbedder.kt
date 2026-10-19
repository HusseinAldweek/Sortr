package com.sortr.data.faces

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.sortr.domain.faces.FaceMath
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs the MobileFaceNet model (LiteRT) on an aligned face crop and returns an L2-normalised
 * embedding. Input and output shapes are read from the model file, so 128- or 192-value models
 * and batch sizes above one all work.
 *
 * Expected model: input [N, S, S, 3] float32 RGB scaled to about −1…1 ((value − 127.5) / 128),
 * or uint8 0–255; output [N, D] float32. Only the first slot of a batch is used.
 */
class FaceEmbedder private constructor(private val interpreter: Interpreter) : Closeable {

    /** Side of the square face crop the model expects, usually 112. */
    val inputSize: Int
    val embeddingSize: Int
    private val batch: Int
    private val quantizedInput: Boolean
    private val input: ByteBuffer
    private val output: ByteBuffer
    private val pixels: IntArray

    init {
        val inputTensor = interpreter.getInputTensor(0)
        val shape = inputTensor.shape()
        require(shape.size == 4 && shape[3] == 3 && shape[1] == shape[2] && shape[1] in 32..512) {
            "Unsupported model input shape ${shape.contentToString()}"
        }
        quantizedInput = when (inputTensor.dataType()) {
            DataType.FLOAT32 -> false
            DataType.UINT8 -> true
            else -> throw IllegalArgumentException("Unsupported model input type ${inputTensor.dataType()}")
        }
        batch = shape[0].coerceAtLeast(1)
        inputSize = shape[1]

        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        require(outputTensor.dataType() == DataType.FLOAT32) { "Unsupported model output type ${outputTensor.dataType()}" }
        embeddingSize = outputShape.last()
        require(embeddingSize in 64..2048 && outputTensor.numElements() == batch * embeddingSize) {
            "Unsupported model output shape ${outputShape.contentToString()}"
        }

        input = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())
        output = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
        pixels = IntArray(inputSize * inputSize)
    }

    @Synchronized
    fun embed(face: Bitmap): FloatArray {
        require(face.width == inputSize && face.height == inputSize) { "Face crop must be $inputSize×$inputSize" }
        face.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        input.rewind()
        repeat(batch) {
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (quantizedInput) {
                    input.put(r.toByte()).put(g.toByte()).put(b.toByte())
                } else {
                    input.putFloat((r - 127.5f) / 128f).putFloat((g - 127.5f) / 128f).putFloat((b - 127.5f) / 128f)
                }
            }
        }
        input.rewind()
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        return FaceMath.l2Normalize(FloatArray(embeddingSize) { output.getFloat() })
    }

    override fun close() = interpreter.close()

    companion object {
        fun load(assets: AssetManager, path: String): FaceEmbedder {
            val bytes = assets.open(path).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            model.put(bytes).rewind()
            val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
            return try {
                FaceEmbedder(interpreter)
            } catch (e: Exception) {
                interpreter.close()
                throw e
            }
        }
    }
}
