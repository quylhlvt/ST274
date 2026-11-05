package com.oc.catemoji.catoc.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class BarkDetectionUpdate(val level: Float, val detected: Boolean = false)

/** Shared language-independent microphone capture for Play and Training. */
internal class BarkDetectionManager(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun listen(): Flow<BarkDetectionUpdate> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) throw SecurityException("RECORD_AUDIO permission not granted")

        captureSession.withLock {
            BarkAudioClassifier(context).use { classifier ->
                currentCoroutineContext().ensureActive()
                val segmenter = BarkEventSegmenter()
                val pcm = ShortArray(BarkEventSegmenter.BLOCK_SAMPLES)
                DeviceMicrophone.open(context, BarkEventSegmenter.SAMPLE_RATE, pcm.size).use { microphone ->
                    emit(BarkDetectionUpdate(0f))
                    while (currentCoroutineContext().isActive) {
                        val count = microphone.recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                        check(count >= 0) { "Bark microphone read failed: $count" }
                        if (count == 0) continue
                        currentCoroutineContext().ensureActive()
                        val eventLength = segmenter.accept(pcm, count)
                        val detected = eventLength > 0 && classifier.isBark(segmenter.event, eventLength)
                        emit(BarkDetectionUpdate(segmenter.level, detected))
                    }
                }
            }
        }
    }.catch { error ->
        if (error is LinkageError) throw IllegalStateException("Cannot load the bark classifier", error)
        throw error
    }.flowOn(Dispatchers.IO)

    private companion object {
        val captureSession = Mutex()
    }
}
