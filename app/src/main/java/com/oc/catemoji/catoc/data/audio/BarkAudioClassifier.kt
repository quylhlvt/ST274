package com.oc.catemoji.catoc.data.audio

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Acoustic embeddings plus a head trained on human bark imitations and speech negatives. */
internal class BarkAudioClassifier(context: Context) : Closeable {
    private val model = context.assets.open("audio/bark-embedding.tflite").use { stream ->
        val bytes = stream.readBytes()
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
    }
    private val head = context.assets.open("audio/bark-head.bin").use { BarkEmbeddingHead(it.readBytes()) }
    // XNNPACK aborts during tensor allocation for this model on the Galaxy A03s.
    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2).setUseXNNPACK(false))
    private val inputTensor = interpreter.getInputTensor(0)
    private val outputTensor = interpreter.getOutputTensor(0)
    private val input = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
    private val samples = inputTensor.numElements()
    private val features = outputTensor.numElements()
    private val scale = outputTensor.quantizationParams().scale
    private val zeroPoint = outputTensor.quantizationParams().zeroPoint
    private val embedding = FloatArray(features)

    init {
        if (inputTensor.dataType() != DataType.FLOAT32 || head.features != features) {
            interpreter.close()
            error("Incompatible bark embedding model")
        }
    }

    fun isBark(pcm: ShortArray, count: Int): Boolean {
        require(count in 1..minOf(pcm.size, samples))
        val start = (samples - count) / 2
        input.clear()
        repeat(start) { input.putFloat(0f) }
        var maxAbs = 1
        for (i in 0 until count) {
            val a = kotlin.math.abs(pcm[i].toInt())
            if (a > maxAbs) maxAbs = a
        }
        // Chỉ khuếch đại, không làm nhỏ đi: tiếng to giữ nguyên, tiếng nhỏ được kéo lên peak ~0.5
        val gain = (0.5f * 32768f / maxAbs).coerceIn(1f, 8f)
        repeat(count) { input.putFloat((pcm[it] / 32768f * gain).coerceIn(-1f, 1f)) }
        while (input.hasRemaining()) input.putFloat(0f)
        input.rewind()
        output.clear()
        interpreter.run(input, output)
        output.rewind()

        repeat(features) { index ->
            embedding[index] = when (outputTensor.dataType()) {
                DataType.FLOAT32 -> output.float
                DataType.INT8 -> (output.get().toInt() - zeroPoint) * scale
                DataType.UINT8 -> ((output.get().toInt() and 255) - zeroPoint) * scale
                else -> error("Unsupported bark embedding format")
            }
        }
        val s = head.score(embedding)
        android.util.Log.d("BarkDebug", "score=$s threshold=${head.passThreshold} len=${count * 1000 / 16000}ms")
        return s.isFinite() && s >= head.passThreshold- 1.0
    }

    override fun close() = interpreter.close()
}
