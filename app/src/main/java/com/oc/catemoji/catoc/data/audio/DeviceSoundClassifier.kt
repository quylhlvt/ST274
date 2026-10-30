package com.oc.catemoji.catoc.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** One score is awarded for each separated PCM sound burst CONFIRMED by the audio model. */
data class BarkListeningUpdate(
    val prediction: SoundPrediction,
    val newPoints: Int,
    val totalPoints: Int
)

/**
 * Reads the microphone ONCE. PCM capture is independent from model inference.
 * The classifier receives the latest ~1-second window every ~200ms.
 * If inference falls behind, only old inference windows are discarded;
 * PCM capture and burst detection continue without interruption.
 */
class DeviceSoundClassifier(
    private val context: Context
) {
    private data class AudioWindow(
        val pcm: ShortArray,
        val startMs: Long,
        val endMs: Long,
        val recentLevel: Float
    )

    /** Legacy API, for other callers that only need categories. */
    fun listen(): Flow<SoundPrediction> = listenScored().map { it.prediction }

    @SuppressLint("MissingPermission")
    fun listenScored(): Flow<BarkListeningUpdate> = channelFlow {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) { "RECORD_AUDIO permission not granted" }

        Log.i(TAG, "STAGE 1/4: Loading $MODEL_PATH")
        val model = withContext(Dispatchers.Default) {
            try {
                BarkAudioModel.create(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MODEL_INIT failed: ${e.message}", e)
                throw IllegalStateException("MODEL_INIT: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }
        val sampleRate: Int
        val windowSamples: Int
        try {
            sampleRate = model.sampleRate
            windowSamples = model.inputSamples
            require(sampleRate > 0 && windowSamples > 0) {
                "Invalid model input: rate=$sampleRate, samples=$windowSamples"
            }
            Log.i(TAG, "STAGE 2/4: Model OK rate=$sampleRate channels=1 samples=$windowSamples")
        } catch (e: Exception) {
            Log.e(TAG, "MODEL_FORMAT failed: ${e.message}", e)
            model.close()
            throw IllegalStateException("MODEL_FORMAT: ${e.message ?: e.javaClass.simpleName}", e)
        }
        val hopSamples = (sampleRate / 5).coerceAtLeast(1) // ~200ms
        val tracker = BarkBurstScoreTracker(
            sampleRate = sampleRate,
            minimumModelScore = BARK_SCORE_THRESHOLD
        )
        val windows = Channel<AudioWindow>(Channel.CONFLATED)

        val inferenceJob = launch(Dispatchers.Default) {
            try {
                for (window in windows) {
                    val startedAt = SystemClock.elapsedRealtime()
                    val scores = model.classifyPcm16(window.pcm)
                    // The bundled model maps 0=Background Noise and 1=sua (bark).
                    val barkScore = scores.firstOrNull { it.index == BARK_CLASS_INDEX }?.score ?: 0f
                    val update = tracker.offerAiPrediction(
                        barkScore, window.startMs, window.endMs
                    )
                    val inferenceMs = SystemClock.elapsedRealtime() - startedAt
                    Log.d(TAG, "inference=${inferenceMs}ms bark=${"%.2f".format(barkScore)} " +
                            "events=+${update.newPoints} total=${update.totalPoints} " +
                            tracker.diagnosticSummary())
                    trySend(BarkListeningUpdate(
                        prediction = SoundPrediction(scores, window.recentLevel, true),
                        newPoints = update.newPoints,
                        totalPoints = update.totalPoints
                    ))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "INFERENCE failed: ${e.message}", e)
                throw IllegalStateException("INFERENCE: ${e.message ?: e.javaClass.simpleName}", e)
            } finally {
                model.close()
            }
        }

        val captureJob = launch(Dispatchers.IO) {
            Log.i(TAG, "STAGE 3/4: Creating microphone recorder for $sampleRate Hz")
            val audioRecord = try {
                // Always capture PCM16 because the loop reads into ShortArray.
                // Use the sample rate from the model's metadata, not a hardcoded 16kHz.
                createPcm16Recorder(sampleRate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "RECORDER_INIT failed: ${e.message}", e)
                throw IllegalStateException("RECORDER_INIT: ${e.message ?: e.javaClass.simpleName}", e)
            }
            try {
                check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    "AudioRecord not initialized (state=${audioRecord.state})"
                }
                Log.i(TAG, "Recorder format: sampleRate=${audioRecord.sampleRate}, " +
                        "channels=${audioRecord.channelCount}, encoding=${audioRecord.audioFormat}")
                check(audioRecord.audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                    "Wrong recording encoding: ${audioRecord.audioFormat}"
                }
                audioRecord.startRecording()
                check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "AudioRecord is not recording after startRecording()"
                }
                Log.i(TAG, "STAGE 4/4: Microphone started")

                val block = ShortArray(2048)
                var firstRead = true
                val ring = ShortArray(windowSamples)
                var writePosition = 0
                var sampleCount = 0L
                var lastSubmitted = 0L
                var lastLevelAt = 0L
                var lastLevel = 0f
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val n = audioRecord.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        val cause = when (n) {
                            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                            AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                            AudioRecord.ERROR -> "ERROR"
                            else -> "CODE_$n"
                        }
                        error("AudioRecord.read failed: $cause ($n), encoding=${audioRecord.audioFormat}")
                    }
                    if (n == 0) continue
                    if (firstRead) {
                        Log.i(TAG, "Mic received first PCM block: $n samples")
                        firstRead = false
                    }

                    var power = 0.0
                    for (i in 0 until n) {
                        val sample = block[i]
                        val normalized = sample / 32768.0
                        power += normalized * normalized
                        ring[writePosition] = sample
                        writePosition = (writePosition + 1) % ring.size
                    }
                    sampleCount += n
                    tracker.offerPcm(block, n)
                    lastLevel = sqrt(power / n).toFloat().coerceIn(0f, 1f)

                    // Keep waveform responsive without producing 100+ UI updates / sec.
                    if (sampleCount - lastLevelAt >= sampleRate / 10) {
                        Log.d(TAG, "mic level=${"%.4f".format(lastLevel)}")
                        lastLevelAt = sampleCount
                        trySend(BarkListeningUpdate(
                            SoundPrediction(emptyList(), lastLevel, false),
                            newPoints = 0,
                            totalPoints = tracker.currentPoints()
                        ))
                    }

                    if (sampleCount >= windowSamples &&
                        sampleCount - lastSubmitted >= hopSamples) {
                        lastSubmitted = sampleCount
                        // A coherent copy of the latest model-sized PCM ring.
                        val snapshot = ShortArray(windowSamples)
                        for (i in snapshot.indices) {
                            snapshot[i] = ring[(writePosition + i) % ring.size]
                        }
                        val startMs = (sampleCount - windowSamples) * 1000L / sampleRate
                        val endMs = sampleCount * 1000L / sampleRate
                        windows.trySend(AudioWindow(snapshot, startMs, endMs, lastLevel))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MIC_CAPTURE failed: ${e.message}", e)
                throw IllegalStateException("MIC_CAPTURE: ${e.message ?: e.javaClass.simpleName}", e)
            } finally {
                try {
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                } catch (_: IllegalStateException) { }
                audioRecord.release()
                windows.close()
            }
        }

        try {
            captureJob.join()
        } finally {
            captureJob.cancel()
            windows.close()
            inferenceJob.cancelAndJoin()
        }
    }

    /** Create exactly the PCM16 format expected by read(ShortArray). */
    @SuppressLint("MissingPermission")
    private fun createPcm16Recorder(sampleRate: Int): AudioRecord {
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBytes > 0) {
            "AudioRecord doesn't support PCM16 mono at $sampleRate Hz " +
                    "(getMinBufferSize=$minBytes)"
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBytes, 8192)
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Recorder failed to initialize at $sampleRate Hz PCM16")
        }
        return record
    }

    companion object {
        const val MODEL_PATH = BarkAudioModel.MODEL_PATH
        const val BARK_CLASS_INDEX = 1
        const val BARK_SCORE_THRESHOLD = 0.85f

        private const val TAG = "BarkClassifier"
    }
}
