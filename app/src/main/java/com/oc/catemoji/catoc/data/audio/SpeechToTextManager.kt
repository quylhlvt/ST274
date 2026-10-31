package com.oc.catemoji.catoc.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONObject
import org.vosk.Recognizer
import kotlin.math.roundToLong
import kotlin.math.sqrt

data class SpeechRecognitionUpdate(
    val text: String,
    val isFinal: Boolean,
    val level: Float,
    val words: List<BarkSpeechWord> = emptyList(),
    val partialText: String? = null,
)

/** Offline Vietnamese speech, with one PCM16 recorder and native decoding confined to IO. */
class SpeechToTextManager(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun listen(): Flow<SpeechRecognitionUpdate> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) throw SecurityException("RECORD_AUDIO permission not granted")

        VoskVietnameseModel.open(context).use { model ->
            // Recognize only the three playable sounds used to imitate barking.
            Recognizer(model, SAMPLE_RATE.toFloat(), GRAMMAR).use { recognizer ->
                recognizer.setWords(true)
                recognizer.setPartialWords(true)
                currentCoroutineContext().ensureActive()
                val minimumBytes = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                )
                check(minimumBytes > 0) { "PCM16 recording is unavailable at $SAMPLE_RATE Hz" }
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimumBytes, BLOCK_SAMPLES * Short.SIZE_BYTES * 2),
                )
                try {
                    check(recorder.state == AudioRecord.STATE_INITIALIZED)
                    recorder.startRecording()
                    check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    emit(SpeechRecognitionUpdate("", false, 0f))
                    val pcm = ShortArray(BLOCK_SAMPLES)
                    var previousText = ""
                    var previousWords = emptyList<BarkSpeechWord>()
                    while (currentCoroutineContext().isActive) {
                        val count = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                        check(count >= 0) { "Speech microphone read failed: $count" }
                        if (count == 0) continue
                        var power = 0.0
                        for (index in 0 until count) {
                            val sample = pcm[index] / 32768.0
                            power += sample * sample
                        }
                        val final = recognizer.acceptWaveForm(pcm, count)
                        val update = readUpdate(recognizer, final, sqrt(power / count).toFloat())
                        val text = update.partialText ?: update.text
                        val words = update.words
                        if (final || text != previousText || words != previousWords) {
                            Log.d(TAG, "${if (final) "result" else "partial"}: $text words=$words")
                            previousText = if (final) "" else text
                            previousWords = if (final) emptyList() else words
                        }
                        emit(update)
                    }
                } finally {
                    // Cancelling a session prevents further callbacks.
                    try {
                        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                    } finally {
                        recorder.release()
                    }
                }
            }
        }
    }.catch { error ->
        if (error is LinkageError) throw IllegalStateException("Cannot load Vosk native speech engine", error)
        throw error
    }.flowOn(Dispatchers.IO)

    companion object {
        internal fun readUpdate(recognizer: Recognizer, isFinal: Boolean, level: Float): SpeechRecognitionUpdate {
            if (isFinal) return parseResult(recognizer.result, true, level)
            val fast = JSONObject(recognizer.partialResult).optString("partial")
            return SpeechRecognitionUpdate(fast, false, level, emptyList(), fast)
        }
        internal fun parseResult(result: String, isFinal: Boolean, level: Float): SpeechRecognitionUpdate {
            val json = JSONObject(result)
            val aligned = json.optJSONArray(if (isFinal) "result" else "partial_result")
            val words = if (aligned != null) (0 until aligned.length()).map { index ->
                val word = aligned.getJSONObject(index)
                val start = word.optDouble("start", Double.NaN)
                val end = word.optDouble("end", Double.NaN)
                BarkSpeechWord(
                    word.optString("word"),
                    if (start.isFinite() && start >= 0) (start * 1_000).roundToLong() else -1L,
                    if (end.isFinite() && end >= 0) (end * 1_000).roundToLong() else -1L,
                    word.optDouble("conf", 0.0).toFloat(),
                )
            } else emptyList()
            return SpeechRecognitionUpdate(json.optString(if (isFinal) "text" else "partial"), isFinal, level, words)
        }

        const val SAMPLE_RATE = 16_000
        const val GRAMMAR = "[\"gâu\",\"go\",\"gô\"]"
        private const val BLOCK_SAMPLES = 600 // 40ms; do not collect a phrase before decoding.
        private const val TAG = "BarkSpeech"
    }
}
