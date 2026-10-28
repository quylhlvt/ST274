package com.oc.catemoji.catoc.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.core.BaseOptions
import javax.inject.Inject
import kotlin.math.sqrt

/** One foreground session owns its recorder and native model, and releases both on cancellation. */
class DeviceSoundClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun listen(): Flow<SoundPrediction> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission missing")
        }
        val classifier = try {
            AudioClassifier.createFromFileAndOptions(
                context, MODEL_PATH,
                AudioClassifier.AudioClassifierOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setNumThreads(2).build())
                    .setScoreThreshold(0f)
                    .setMaxResults(-1)
                    .build(),
            )
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException("Audio classifier runtime unavailable", error)
        }
        var recorder: AudioRecord? = null
        try {
            val audio = classifier.createInputTensorAudio()
            val requiredSamples = classifier.requiredInputBufferSize.toInt()
            val record = classifier.createAudioRecord().also { recorder = it }
            val samples = FloatArray(record.bufferSizeInFrames * record.channelCount)
            var filledSamples = 0
            var emptyReads = 0
            var lastInferenceAt = 0L
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
            emit(SoundPrediction(emptyList(), 0f, hasClassification = false))
            while (currentCoroutineContext().isActive) {
                val count = record.read(samples, 0, samples.size, AudioRecord.READ_NON_BLOCKING)
                check(count >= 0) { "Microphone read failed: $count" }
                if (count > 0) {
                    emptyReads = 0
                    audio.load(samples, 0, count)
                    filledSamples = (filledSamples + count).coerceAtMost(requiredSamples)
                    var sum = 0.0
                    for (index in 0 until count) sum += samples[index].toDouble() * samples[index]
                    val level = sqrt(sum / count).toFloat().coerceIn(0f, 1f)
                    val now = SystemClock.elapsedRealtime()
                    val classifyNow = filledSamples >= requiredSamples && level >= 0.00001f &&
                        now - lastInferenceAt >= INFERENCE_INTERVAL_MS
                    val scores = if (classifyNow) {
                        lastInferenceAt = now
                        classifier.classify(audio).flatMap { head ->
                            head.categories.map { category ->
                                SoundScore(category.index, cleanLabel(category.label), category.score)
                            }
                        }
                    } else emptyList()
                    emit(SoundPrediction(scores, level, hasClassification = classifyNow))
                } else {
                    emptyReads++
                    check(emptyReads < MAX_EMPTY_READS) { "Microphone stopped providing audio" }
                }
                delay(POLL_INTERVAL_MS)
            }
        } finally {
            try {
                recorder?.let { record ->
                    try {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                    } finally {
                        record.release()
                    }
                }
            } finally {
                classifier.close()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun cleanLabel(label: String): String = label.replace(LABEL_PREFIX, "").trim()

    companion object {
        private val LABEL_PREFIX = Regex("^[0-9]+ +")
        const val MODEL_PATH = "tflite/soundclassifier_with_metadata.tflite"
        private const val POLL_INTERVAL_MS = 50L
        private const val INFERENCE_INTERVAL_MS = 200L
        private const val MAX_EMPTY_READS = 100
    }
}
