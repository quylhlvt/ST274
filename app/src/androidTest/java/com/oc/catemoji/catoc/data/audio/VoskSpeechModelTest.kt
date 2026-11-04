package com.oc.catemoji.catoc.data.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class VoskSpeechModelTest {
    @Test
    fun oneBarkAtDifferentCaptureOffsetsScoresExactlyOnce() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val voice = fixture("gau")
        VoskVietnameseModel.open(context).use { model ->
            // Include the old fixed-window boundaries: elapsed recording time must
            // neither cut a single bark into two nor hide a completely missed bark.
            listOf(3_200, 8_000, 11_200).forEach { leadingSamples ->
                val trace = recognize(model, padded(voice, leadingSamples))
                assertEquals("gau at offset $leadingSamples: $trace", 1, trace.count)
            }
        }
    }

    @Test
    fun conditionedBarksScoreAndOtherWordsAreRejectedWithBackgroundHissAndRumble() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        VoskVietnameseModel.open(context).use { model ->
            listOf("gau", "go", "repeatedGau", "mo", "vang", "giang").forEach { name ->
                val word = fixture(if (name == "repeatedGau") "gau" else name)
                val voice = if (name == "repeatedGau") ShortArray((word.size + 2_400) * 3).also { repeated ->
                    repeat(3) { word.copyInto(repeated, it * (word.size + 2_400)) }
                } else word
                val rms = sqrt(voice.sumOf { it.toDouble() * it } / voice.size)
                listOf(15.0, 10.0).forEach { snr ->
                    val random = Random(274)
                    val noiseRms = rms / 10.0.pow(snr / 20)
                    val input = ShortArray(SAMPLE_RATE + voice.size + SAMPLE_RATE * 3) { i ->
                        val speech = voice.getOrElse(i - SAMPLE_RATE) { 0 }.toDouble()
                        val hiss = (random.nextDouble() * 2 - 1) * sqrt(3.0) * noiseRms
                        val rumble = noiseRms * sin(2 * PI * 45 * i / SAMPLE_RATE)
                        (speech + hiss + rumble).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    val trace = recognize(model, input)
                    val expected = when (name) {
                        "gau", "go" -> 1
                        "repeatedGau" -> 3
                        else -> 0
                    }
                    assertEquals("$name at $snr dB: $trace", expected, trace.count)
                    if (expected > 0) {
                        // A short acoustic final is allowed; the old test demanded
                        // an unconfirmed singleton partial before any endpoint.
                        val deadline = samplesToMs(SAMPLE_RATE + voice.size) + 400
                        assertTrue("$name at $snr dB did not score promptly: $trace",
                            trace.scores.last().audioTimeMs <= deadline)
                    }
                }
            }
        }
    }

    @Test fun partialWordAlignmentSurvivesParsing() {
        val update = SpeechToTextManager.parseResult(
            """{"partial":"gâu gâu","partial_result":[
                {"word":"gâu","start":0.1,"end":0.3,"conf":0.7},
                {"word":"gâu","start":0.3,"end":0.5,"conf":0.8}]}""", false, 0.1f,
        )
        assertEquals(listOf(100L, 300L), update.words.map { it.startMs })
        assertEquals(2, update.words.size)
    }

    @Test
    fun spokenBarksScoreAfterAShortGapWhileConversationAndNearWordsStayAtZero() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        VoskVietnameseModel.open(context).use { model ->
            listOf("gau", "go", "mo", "vang", "giang", "greeting", "counting", "weather", "near-bark")
                .forEach { name ->
                    val voice = fixture(name)
                    val trace = recognize(model, padded(voice))
                    val playable = name == "gau" || name == "go"
                    assertEquals("$name: $trace", if (playable) 1 else 0, trace.count)
                    if (playable) {
                        val audibleEnd = samplesToMs(SAMPLE_RATE + lastAudibleSample(voice) + 1)
                        val deadline = audibleEnd + 320
                        assertTrue("$name waited for Vosk's long phrase endpoint: $trace",
                            trace.scores.single().audioTimeMs <= deadline)
                        assertTrue("$name never finalized at its short acoustic gap: $trace",
                            trace.acousticEndpoints.any { it in audibleEnd..deadline })
                    }
                }
        }
    }

    @Test
    fun rapidRepeatedBarksAllScoreWithoutWaitingForALongPhraseEndpoint() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val voice = fixture("gau-repeat")
        VoskVietnameseModel.open(context).use { model ->
            val trace = recognize(model, padded(voice))
            assertEquals("gau-repeat: $trace", 3, trace.count)
            val deadline = samplesToMs(SAMPLE_RATE + lastAudibleSample(voice) + 1) + 320
            assertTrue("rapid barks waited for a long phrase endpoint: $trace",
                trace.scores.last().audioTimeMs <= deadline)
        }
    }

    @Test
    fun bundledModelInstallsReopensAndRecognizesSilenceWithoutBarkPoints() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // First pass installs the archive; the second reuses the completed installation.
        repeat(2) {
            VoskVietnameseModel.open(context).use { model ->
                val trace = recognize(model, ShortArray(SAMPLE_RATE * 3))
                assertEquals("silence: $trace", 0, trace.count)
            }
        }
    }

    private data class ScoreEvent(val audioTimeMs: Long, val added: Int, val source: String)

    private data class RecognitionTrace(
        val count: Int,
        val scores: List<ScoreEvent>,
        val acousticEndpoints: List<Long>,
        val hypotheses: Set<String>,
    )

    /** Exercise the same conditioning, timing and endpoint policy as live microphone capture. */
    private fun recognize(model: Model, input: ShortArray): RecognitionTrace {
        val processor = PcmSpeechProcessor(SAMPLE_RATE)
        val window = SpeechWindowPolicy(SAMPLE_RATE)
        val gate = PcmSpeechGate(SAMPLE_RATE, SpeechToTextManager.BLOCK_SAMPLES)
        val counter = BarkSpeechCounter()
        val scores = mutableListOf<ScoreEvent>()
        val acousticEndpoints = mutableListOf<Long>()
        val hypotheses = linkedSetOf<String>()
        val block = ShortArray(SpeechToTextManager.BLOCK_SAMPLES)
        var capturedSamples = 0L

        fun accept(update: SpeechRecognitionUpdate, source: String) {
            val before = counter.count
            counter.accept(update.text, update.isFinal, update.words, update.partialText, update.audioTimeMs)
            if (counter.count > before) scores.add(ScoreEvent(update.audioTimeMs, counter.count - before, source))
            val hypothesis = "${update.partialText ?: update.text}/${update.text}: ${update.words}"
            if (update.text.isNotBlank() || !update.partialText.isNullOrBlank()) hypotheses.add(hypothesis)
        }

        Recognizer(model, SAMPLE_RATE.toFloat(), SpeechToTextManager.GRAMMAR).use { recognizer ->
            recognizer.setWords(true)
            recognizer.setPartialWords(true)
            for (offset in input.indices step block.size) {
                val count = minOf(block.size, input.size - offset)
                input.copyInto(block, 0, offset, offset + count)
                val level = processor.process(block, count)
                capturedSamples += count
                val audioTimeMs = capturedSamples * 1_000 / SAMPLE_RATE
                val gateWasOpen = gate.isOpen
                val decoderCount = gate.process(block, count)
                if (decoderCount == 0) continue
                if (!gateWasOpen) window.onVoicedOnset()
                val endpoint = recognizer.acceptWaveForm(gate.buffer, decoderCount)
                accept(SpeechToTextManager.readUpdate(recognizer, endpoint, level).copy(audioTimeMs = audioTimeMs),
                    if (endpoint) "recognizer endpoint" else "partial")
                if (endpoint) {
                    window.reset()
                    gate.reset()
                } else if (window.accept(processor.signalLevel, count)) {
                    acousticEndpoints.add(audioTimeMs)
                    accept(SpeechToTextManager.parseResult(recognizer.finalResult, true, level)
                        .copy(audioTimeMs = audioTimeMs), "acoustic endpoint")
                    recognizer.reset()
                    gate.reset()
                }
            }
            // Drain any remaining hypothesis so a late false positive cannot hide.
            // Positive latency assertions ensure this drain cannot mask a missed gap.
            accept(SpeechToTextManager.parseResult(recognizer.finalResult, true, 0f)
                .copy(audioTimeMs = capturedSamples * 1_000 / SAMPLE_RATE), "input end")
        }
        return RecognitionTrace(counter.count, scores, acousticEndpoints, hypotheses)
    }

    private fun fixture(name: String): ShortArray {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bytes = context.assets.open("bark-vn/$name.pcm").use { it.readBytes() }
        return ShortArray(bytes.size / 2) { i ->
            ((bytes[i * 2].toInt() and 255) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
        }
    }

    private fun padded(voice: ShortArray, leadingSamples: Int = SAMPLE_RATE): ShortArray =
        ShortArray(leadingSamples + voice.size + SAMPLE_RATE * 2).also { voice.copyInto(it, leadingSamples) }

    private fun lastAudibleSample(voice: ShortArray): Int {
        val threshold = maxOf(197, voice.maxOf { abs(it.toInt()) } / 100)
        return voice.indexOfLast { abs(it.toInt()) >= threshold }.coerceAtLeast(0)
    }

    private fun samplesToMs(samples: Int): Long = samples.toLong() * 1_000 / SAMPLE_RATE

    private companion object {
        const val SAMPLE_RATE = SpeechToTextManager.SAMPLE_RATE
    }
}
