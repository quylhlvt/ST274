package com.oc.catemoji.catoc.data.audio

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** LiteRT-only wrapper for the bundled two-class bark model. */
class BarkAudioModel private constructor(
    private val interpreter: Interpreter,
    val sampleRate: Int,
    val inputSamples: Int,
    private val labels: List<String>,
) : Closeable {
    private val inputBuffer = ByteBuffer.allocateDirect(inputSamples * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
    private val outputBuffer = ByteBuffer.allocateDirect(labels.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    fun classifyPcm16(pcm: ShortArray): List<SoundScore> {
        require(pcm.size == inputSamples) {
            "Expected $inputSamples PCM samples, received ${pcm.size}"
        }

        inputBuffer.clear()
        for (sample in pcm) inputBuffer.putFloat(sample / PCM_NORMALIZATION)
        inputBuffer.rewind()
        outputBuffer.clear()
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        return labels.mapIndexed { index, label ->
            // The model can emit NaN for an all-zero PCM window. Treat that
            // degenerate input as no confidence instead of stopping capture.
            val score = outputBuffer.float.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
            SoundScore(index, label, score)
        }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val INPUT_SAMPLES = 44_032
        const val MODEL_PATH = "tflite/soundclassifier_with_metadata.tflite"
        const val LABELS_PATH = "tflite/labels.txt"

        private const val PCM_NORMALIZATION = 32_768f
        private const val OUTPUT_CLASSES = 2

        fun create(context: Context): BarkAudioModel {
            val labels = context.assets.open(LABELS_PATH).bufferedReader().useLines { lines ->
                lines.map(String::trim).filter(String::isNotEmpty).toList()
            }
            require(labels.size == OUTPUT_CLASSES) {
                "Expected $OUTPUT_CLASSES labels, found ${labels.size} in $LABELS_PATH"
            }

            val interpreter = Interpreter(
                loadMappedModel(context),
                Interpreter.Options().apply {
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                },
            )
            try {
                val input = interpreter.getInputTensor(0)
                require(input.dataType() == DataType.FLOAT32) {
                    "Expected FLOAT32 input, found ${input.dataType()}"
                }
                require(input.numBytes() == INPUT_SAMPLES * Float.SIZE_BYTES) {
                    "Expected $INPUT_SAMPLES input samples, shape=${input.shape().contentToString()}"
                }

                val output = interpreter.getOutputTensor(0)
                require(output.dataType() == DataType.FLOAT32) {
                    "Expected FLOAT32 output, found ${output.dataType()}"
                }
                require(output.numBytes() == labels.size * Float.SIZE_BYTES) {
                    "Expected ${labels.size} output scores, shape=${output.shape().contentToString()}"
                }
                return BarkAudioModel(interpreter, SAMPLE_RATE, INPUT_SAMPLES, labels)
            } catch (error: Exception) {
                interpreter.close()
                throw error
            }
        }

        private fun loadMappedModel(context: Context): MappedByteBuffer =
            context.assets.openFd(MODEL_PATH).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
    }
}
