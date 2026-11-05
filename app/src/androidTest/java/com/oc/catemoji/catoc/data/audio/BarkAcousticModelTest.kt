package com.oc.catemoji.catoc.data.audio

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarkAcousticModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var foreground: ActivityScenario<AudioTestActivity>

    @Before fun keepInferenceInForeground() {
        foreground = ActivityScenario.launch(Intent(instrumentation.targetContext,
            AudioTestActivity::class.java))
    }

    @After fun closeForeground() = foreground.close()

    @Test fun humanImitationsFromFourHeldOutSpeakersEachScoreOnce() {
        BarkAudioClassifier(instrumentation.targetContext).use { classifier ->
            for (index in 1..4) {
                val bark = fixture("bark-$index")
                assertEquals("Human imitation $index", 1, detect(classifier, padded(bark)))
            }
        }
    }

    @Test fun rapidBarksKeepScoringAtEveryShortGapWithoutDuplicatePoints() {
        BarkAudioClassifier(instrumentation.targetContext).use { classifier ->
            for (index in 1..4) {
                val bark = fixture("bark-$index")
                val stride = bark.size + 960 // 60 ms between consecutive imitations.
                val repeated = ShortArray(stride * 10)
                repeat(10) { bark.copyInto(repeated, it * stride) }
                assertEquals("Rapid imitations from speaker $index", 10, detect(classifier, padded(repeated)))
            }
        }
    }

    @Test fun aPreviouslyMissedHumanImitationScoresOnce() {
        BarkAudioClassifier(instrumentation.targetContext).use { classifier ->
            assertEquals(1, detect(classifier, padded(fixture("bark-previously-missed"))))
        }
    }

    @Test fun ordinarySpeechInFourLanguagesAndSilenceDoNotScore() {
        BarkAudioClassifier(instrumentation.targetContext).use { classifier ->
            for (language in listOf("en", "de", "fr", "es")) {
                assertEquals("Ordinary speech: $language", 0, detect(classifier, padded(fixture("speech-$language"))))
            }
            assertEquals(0, detect(classifier, ShortArray(16_000 * 5)))
        }
    }

    @Test fun heldOutDeskKnocksDoNotScoreAtDifferentVolumes() {
        BarkAudioClassifier(instrumentation.targetContext).use { classifier ->
            for (id in listOf("244325", "388269", "560225")) {
                val knocks = fixture("desk-knock-$id")
                for (gain in listOf(0.35, 0.65, 1.0, 1.8, 3.0)) {
                    val pcm = ShortArray(knocks.size) { index ->
                        (knocks[index] * gain).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    assertEquals("Desk knock $id at gain $gain", 0, detect(classifier, padded(pcm)))
                }
            }
        }
    }

    private fun detect(classifier: BarkAudioClassifier, pcm: ShortArray): Int {
        val segmenter = BarkEventSegmenter()
        val block = ShortArray(BarkEventSegmenter.BLOCK_SAMPLES)
        var count = 0
        for (offset in pcm.indices step block.size) {
            val size = minOf(block.size, pcm.size - offset)
            pcm.copyInto(block, 0, offset, offset + size)
            val samples = segmenter.accept(block, size)
            if (samples > 0 && classifier.isBark(segmenter.event, samples)) count++
        }
        return count
    }

    private fun fixture(name: String): ShortArray {
        val bytes = instrumentation.context.assets.open("bark-acoustic/$name.pcm").use { it.readBytes() }
        require(bytes.isNotEmpty() && bytes.size % 2 == 0)
        return ShortArray(bytes.size / 2) { index ->
            ((bytes[index * 2].toInt() and 255) or (bytes[index * 2 + 1].toInt() shl 8)).toShort()
        }
    }

    private fun padded(voice: ShortArray): ShortArray = ShortArray(voice.size + 32_000).also {
        voice.copyInto(it, 16_000)
    }
}
