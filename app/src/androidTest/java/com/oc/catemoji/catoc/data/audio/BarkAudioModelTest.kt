package com.oc.catemoji.catoc.data.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import kotlin.math.sin
import kotlin.math.PI

@RunWith(AndroidJUnit4::class)
class BarkAudioModelTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bundledModelLoadsItsRealMetadataAndClassifiesAudio() {
        val classifier = AudioClassifier.createFromFile(context, DeviceSoundClassifier.MODEL_PATH)
        try {
            assertEquals(44_100, classifier.requiredTensorAudioFormat.sampleRate)
            assertEquals(1, classifier.requiredTensorAudioFormat.channels)
            assertEquals(44_032L, classifier.requiredInputBufferSize)
            val tensor = classifier.createInputTensorAudio()
            tensor.load(FloatArray(classifier.requiredInputBufferSize.toInt()))
            val silence = classifier.classify(tensor).single().categories
            val silentPolicy = BarkDetectionPolicy(0)
            repeat(3) {
                assertFalse(silentPolicy.evaluate(SoundPrediction(silence.map {
                    SoundScore(it.index, it.label, it.score)
                }, 0f)).isBark)
            }
            tensor.load(FloatArray(classifier.requiredInputBufferSize.toInt()) { sample ->
                (0.04 * sin(2 * PI * 440 * sample / 44_100) +
                    0.02 * sin(2 * PI * 880 * sample / 44_100)).toFloat()
            })
            val categories = classifier.classify(tensor).single().categories
            assertEquals(setOf(0, 1, 2), categories.map { it.index }.toSet())
            assertTrue("Model scores: ${categories.map { it.label to it.score }}", categories.all { it.score.isFinite() && it.score in 0f..1f })
            assertEquals(setOf("0 Background Noise", "1 Class 3", "2 Other Sounds"), categories.map { it.label }.toSet())
            val prediction = SoundPrediction(categories.map { SoundScore(it.index, it.label, it.score) }, 0f)
            val policy = BarkDetectionPolicy(0)
            repeat(3) { assertFalse(policy.evaluate(prediction).isBark) }
        } finally {
            classifier.close()
        }
    }

    @Test fun stoppingAndRestartingLiveMicrophoneReleasesTheRecordingSession() = runBlocking {
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val audioManager = context.getSystemService(AudioManager::class.java)
        val existingSessions = audioManager.activeRecordingConfigurations.map { it.clientAudioSessionId }.toSet()
        val detector = DeviceSoundClassifier(context)
        repeat(2) {
            val result = withTimeout(10_000) { detector.listen().drop(1).first() }
            assertTrue(result.level.isFinite() && result.level in 0f..1f)
            assertTrue(result.scores.all { it.score.isFinite() })
            withTimeout(3_000) {
                while (audioManager.activeRecordingConfigurations.any {
                        it.clientAudioSessionId != 0 && it.clientAudioSessionId !in existingSessions
                    }) delay(50)
            }
        }
    }
}
