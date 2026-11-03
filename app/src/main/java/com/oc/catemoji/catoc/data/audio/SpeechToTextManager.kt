package com.oc.catemoji.catoc.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToLong

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

        captureSession.withLock {
            VoskVietnameseModel.open(context).use { model ->
                // Unknown sounds compete with bark words instead of being forced into points.
                Recognizer(model, SAMPLE_RATE.toFloat(), GRAMMAR).use { recognizer ->
                    recognizer.setWords(true)
                    recognizer.setPartialWords(true)
                    currentCoroutineContext().ensureActive()
                    DeviceMicrophone.open(context, SAMPLE_RATE, BLOCK_SAMPLES).use { microphone ->
                        val recorder = microphone.recorder
                        val processor = PcmSpeechProcessor(SAMPLE_RATE)
                        emit(SpeechRecognitionUpdate("", false, 0f))
                        val pcm = ShortArray(BLOCK_SAMPLES)
                        var previousText = ""
                        var previousWords = emptyList<BarkSpeechWord>()
                        var windowSamples = 0
                        var windowPeak = 0f
                        var quietBlocks = 0
                        while (currentCoroutineContext().isActive) {
                            val count = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                            check(count >= 0) { "Speech microphone read failed: $count" }
                            if (count == 0) continue
                            val level = processor.process(pcm, count)
                            currentCoroutineContext().ensureActive()
                            val final = recognizer.acceptWaveForm(pcm, count)
                            val update = readUpdate(recognizer, final, level)
                            val text = update.partialText ?: update.text
                            val words = update.words
                            if (final || text != previousText || words != previousWords) {
                                Log.d(TAG, "${if (final) "result" else "partial"}: $text words=$words")
                                previousText = if (final) "" else text
                                previousWords = if (final) emptyList() else words
                            }
                            emit(update)
                            windowSamples += count
                            windowPeak = maxOf(windowPeak, level)
                            quietBlocks = if (level < maxOf(0.012f, windowPeak * 0.18f))
                                quietBlocks + 1 else 0
                            if (windowSamples >= HARD_WINDOW_SAMPLES ||
                                (windowSamples >= MIN_WINDOW_SAMPLES && quietBlocks >= 3)) {
                                // Close between sounds when possible: cutting in the middle
                                // can score one bark in both windows. The hard limit keeps
                                // uninterrupted speech from stalling the counter forever.
                                emit(parseResult(recognizer.finalResult, true, level))
                                recognizer.reset()
                                windowSamples = 0
                                windowPeak = 0f
                                quietBlocks = 0
                                previousText = ""
                                previousWords = emptyList()
                            }
                        }
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
            // Vosk's aligned partial can lag all the way to the endpoint. Read the live
            // best path separately, then retain delayed word spans for deduplication.
            recognizer.setPartialWords(false)
            val fast = JSONObject(recognizer.partialResult).optString("partial")
            recognizer.setPartialWords(true)
            return parseResult(recognizer.partialResult, false, level).copy(partialText = fast)
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
        // Include similar non-bark words as rejection paths; a tiny bark-only grammar
        // otherwise turns words such as "mõ" into a high-confidence "go".
        const val GRAMMAR = "[\"gâu\",\"go\",\"gô\",\"mõ\",\"vàng\",\"giàng\",\"[unk]\"]"
        internal const val BLOCK_SAMPLES = 640 // 40ms; do not collect a phrase before decoding.
        internal const val MIN_WINDOW_SAMPLES = SAMPLE_RATE * 4 / 5
        internal const val HARD_WINDOW_SAMPLES = SAMPLE_RATE * 6 / 5
        private const val TAG = "BarkSpeech"
        // Shared across Play and Training, including cancelled sessions still closing native resources.
        private val captureSession = Mutex()
    }
}
