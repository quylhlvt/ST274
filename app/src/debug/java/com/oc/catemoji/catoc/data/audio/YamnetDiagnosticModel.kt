package com.oc.catemoji.catoc.data.audio

import android.content.Context
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter

internal class YamnetDiagnosticModel(context: Context, custom: Boolean = false) : Closeable {
    private val model = context.assets.open(if (custom) "audio/custom-bark.tflite" else "audio/yamnet.tflite").use { source ->
        val bytes = source.readBytes()
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
    }
    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    val inputSamples = interpreter.getInputTensor(0).numElements()
    val labels = context.assets.open(if (custom) "audio/custom-bark-labels.txt" else "audio/yamnet-labels.txt").bufferedReader().use { it.readLines() }
    private val input = ByteBuffer.allocateDirect(inputSamples * 4).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder())

    fun classify(samples: ShortArray): FloatArray {
        require(samples.size == inputSamples)
        input.clear()
        samples.forEach { input.putFloat(it / 32768f) }
        input.rewind(); output.clear()
        interpreter.run(input, output)
        output.rewind()
        return FloatArray(labels.size) { output.float.let { if (it.isFinite()) it else 0f } }
    }

    override fun close() = interpreter.close()
}
