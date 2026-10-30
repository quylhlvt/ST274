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
)

/** Offline Vietnamese speech, with one PCM16 recorder and native decoding confined to IO. */
class SpeechToTextManager(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun listen(): Flow<SpeechRecognitionUpdate> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) throw SecurityException("RECORD_AUDIO permission not granted")

        VoskVietnameseModel.open(context).use { model ->
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
                        val result = if (final) recognizer.result else recognizer.partialResult
                        val json = JSONObject(result)
                        val text = json.optString(if (final) "text" else "partial")
                        val aligned = json.optJSONArray(if (final) "result" else "partial_result")
                        val words = if (aligned != null) (0 until aligned.length()).map { index ->
                            val word = aligned.getJSONObject(index)
                            val start = word.optDouble("start", Double.NaN)
                            val end = word.optDouble("end", Double.NaN)
                            BarkSpeechWord(
                                word.optString("word"),
                                if (start.isFinite() && start >= 0) (start * 1_000).roundToLong() else -1L,
                                if (end.isFinite() && end >= 0) (end * 1_000).roundToLong() else -1L,
                            )
                        } else emptyList()
                        if (final || text != previousText || words != previousWords) {
                            Log.d(TAG, "${if (final) "result" else "partial"}: $text words=$words")
                            previousText = if (final) "" else text
                            previousWords = if (final) emptyList() else words
                        }
                        emit(SpeechRecognitionUpdate(text, final, sqrt(power / count).toFloat(), words))
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
        const val SAMPLE_RATE = 16_000
        // Restored bark grammar, excluding the aliases removed at the user's request.
        const val GRAMMAR = "[\"gâu\",\"gâu gâu\",\"gầu\",\"gầu gầu\",\"gấu\",\"gấu gấu\"," +
            "\"gao\",\"gao gao\",\"gô\",\"gô gô\",\"gõ\",\"gõ gõ\",\"[unk]\"]"
        private const val BLOCK_SAMPLES = 1_600 // 100ms
        private const val TAG = "BarkSpeech"
    }
}
