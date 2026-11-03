package com.oc.catemoji.catoc.data.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Recognizer
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class VoskSpeechModelTest {
    @Test
    fun oneBarkAcrossWindowBoundaryNeverScoresTwice() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("bark-vn/gau.pcm").use { it.readBytes() }
        val word = ShortArray(bytes.size / 2) { i ->
            ((bytes[i * 2].toInt() and 255) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
        }
        VoskVietnameseModel.open(instrumentation.targetContext).use { model ->
            listOf(3_200, 8_000, 11_200).forEach { leadingSamples ->
                val input = ShortArray(leadingSamples + word.size + 24_000)
                word.copyInto(input, leadingSamples)
                val counter = BarkSpeechCounter()
                val events = mutableListOf<String>()
                val processor = PcmSpeechProcessor(SpeechToTextManager.SAMPLE_RATE)
                Recognizer(model, SpeechToTextManager.SAMPLE_RATE.toFloat(), SpeechToTextManager.GRAMMAR).use { recognizer ->
                    recognizer.setWords(true)
                    recognizer.setPartialWords(true)
                    val block = ShortArray(SpeechToTextManager.BLOCK_SAMPLES)
                    var windowSamples = 0
                    var windowPeak = 0f
                    var quietBlocks = 0
                    for (offset in input.indices step block.size) {
                        block.fill(0)
                        input.copyInto(block, 0, offset, minOf(offset + block.size, input.size))
                        val level = processor.process(block, block.size)
                        val endpoint = recognizer.acceptWaveForm(block, block.size)
                        val update = SpeechToTextManager.readUpdate(recognizer, endpoint, 0f)
                        val before = counter.count
                        counter.accept(update.text, endpoint, update.words, update.partialText)
                        if (counter.count != before) events.add("${offset / 16_000.0} partial=${update.partialText} text=${update.text}")
                        windowSamples += block.size
                        windowPeak = maxOf(windowPeak, level)
                        quietBlocks = if (level < maxOf(0.012f, windowPeak * 0.18f)) quietBlocks + 1 else 0
                        if (windowSamples >= SpeechToTextManager.HARD_WINDOW_SAMPLES ||
                            (windowSamples >= SpeechToTextManager.MIN_WINDOW_SAMPLES && quietBlocks >= 3)) {
                            val end = SpeechToTextManager.parseResult(recognizer.finalResult, true, 0f)
                            val beforeEnd = counter.count
                            counter.accept(end.text, true, end.words)
                            if (counter.count != beforeEnd) events.add("${offset / 16_000.0} final=${end.text}")
                            recognizer.reset()
                            windowSamples = 0
                            windowPeak = 0f
                            quietBlocks = 0
                        }
                    }
                    assertTrue("one gau at offset $leadingSamples scored ${counter.count}: $events", counter.count <= 1)
                }
            }
        }
    }

    @Test
    fun conditionedBarksScoreAndOtherWordsAreRejectedWithBackgroundHissAndRumble() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        VoskVietnameseModel.open(instrumentation.targetContext).use { model ->
            listOf("gau", "go", "repeatedGau", "mo", "vang", "giang").forEach { fixture ->
                val source = if (fixture == "repeatedGau") "gau" else fixture
                val bytes = instrumentation.context.assets.open("bark-vn/$source.pcm").use { it.readBytes() }
                val word = ShortArray(bytes.size / 2) { i ->
                    ((bytes[i * 2].toInt() and 255) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
                }
                val voice = if (fixture == "repeatedGau") ShortArray((word.size + 2400) * 3).also { repeated ->
                    repeat(3) { word.copyInto(repeated, it * (word.size + 2400)) }
                } else word
                val rms = sqrt(voice.sumOf { it.toDouble() * it } / voice.size)
                listOf(15.0, 10.0).forEach { snr ->
                    val random = Random(274)
                    val noiseRms = rms / 10.0.pow(snr / 20)
                    val input = ShortArray(16_000 + voice.size + 48_000) { i ->
                        val speech = voice.getOrElse(i - 16_000) { 0 }.toDouble()
                        val hiss = (random.nextDouble() * 2 - 1) * sqrt(3.0) * noiseRms
                        val rumble = noiseRms * sin(2 * PI * 45 * i / 16_000)
                        (speech + hiss + rumble).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    Recognizer(model, 16_000f, SpeechToTextManager.GRAMMAR).use { recognizer ->
                        recognizer.setWords(true)
                        recognizer.setPartialWords(true)
                        val processor = PcmSpeechProcessor(16_000)
                        val counter = BarkSpeechCounter()
                        val transcripts = mutableSetOf<String>()
                        var partialScored = false
                        val block = ShortArray(SpeechToTextManager.BLOCK_SAMPLES)
                        for (offset in input.indices step block.size) {
                            val count = minOf(block.size, input.size - offset)
                            input.copyInto(block, 0, offset, offset + count)
                            val level = processor.process(block, count)
                            val endpoint = recognizer.acceptWaveForm(block, count)
                            val update = SpeechToTextManager.readUpdate(recognizer, endpoint, level)
                            transcripts.add("${update.partialText}/${update.text}")
                            val before = counter.count
                            counter.accept(update.text, endpoint, update.words, update.partialText)
                            if (!endpoint && counter.count > before) partialScored = true
                        }
                        val end = SpeechToTextManager.parseResult(recognizer.finalResult, true, 0f)
                        counter.accept(end.text, true, end.words)
                        val playable = fixture in listOf("gau", "go", "repeatedGau")
                        if (playable) assertTrue("$fixture at $snr dB must score from the live partial: $transcripts; final=${end.text}", partialScored)
                        val expected = if (!playable) 0 else if (fixture == "repeatedGau") 3 else 1
                        assertEquals("$fixture at $snr dB: $transcripts; final=${end.text}", expected, counter.count)
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
    fun spokenGauAndGoScoreFromPartialWhileMoVangGiangStayAtZero() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        VoskVietnameseModel.open(instrumentation.targetContext).use { model ->
            listOf("gau", "go", "mo", "vang", "giang").forEach { fixture ->
                val bytes = instrumentation.context.assets.open("bark-vn/$fixture.pcm").use { it.readBytes() }
                val spokenWord = ShortArray(bytes.size / 2) { index ->
                    ((bytes[index * 2].toInt() and 0xff) or (bytes[index * 2 + 1].toInt() shl 8)).toShort()
                }
                // A microphone is already open before speaking. Supply that leading
                // acoustic context instead of cutting directly into the first phone.
                val speech = ShortArray(16_000 + spokenWord.size)
                spokenWord.copyInto(speech, 16_000)
                Recognizer(model, SpeechToTextManager.SAMPLE_RATE.toFloat(), SpeechToTextManager.GRAMMAR).use { recognizer ->
                    recognizer.setWords(true)
                    recognizer.setPartialWords(true)
                    val counter = BarkSpeechCounter()
                    var scoredBeforeEndpoint = false
                    val transcripts = mutableListOf<String>()
                    val block = ShortArray(640)
                    // Append silence so the same isolated word reaches both partial and final paths.
                    for (offset in 0 until speech.size + 48_000 step block.size) {
                        block.fill(0)
                        if (offset < speech.size) speech.copyInto(block, 0, offset, minOf(offset + block.size, speech.size))
                        val endpoint = recognizer.acceptWaveForm(block, block.size)
                        val update = SpeechToTextManager.readUpdate(recognizer, endpoint, 0.5f)
                        val before = counter.count
                        counter.accept(update.text, update.isFinal, update.words, update.partialText)
                        if (!endpoint && counter.count > before) scoredBeforeEndpoint = true
                        if (update.text.isNotBlank()) transcripts.add("${update.text}: ${update.words}")
                    }
                    val end = SpeechToTextManager.parseResult(recognizer.finalResult, true, 0f)
                    counter.accept(end.text, true, end.words)
                    val playable = fixture == "gau" || fixture == "go"
                    assertEquals("$fixture: ${transcripts.distinct()}", if (playable) 1 else 0, counter.count)
                    if (playable) assertTrue("One $fixture must score without waiting for the phrase to end", scoredBeforeEndpoint)
                }
            }
        }
    }

    @Test
    fun bundledModelInstallsReopensAndRecognizesSilenceWithoutBarkPoints() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // First pass installs the archive; the second reuses the completed installation.
        repeat(2) {
            VoskVietnameseModel.open(context).use { model ->
                Recognizer(model, SpeechToTextManager.SAMPLE_RATE.toFloat(), SpeechToTextManager.GRAMMAR).use { recognizer ->
                    recognizer.setWords(true)
                    recognizer.setPartialWords(true)
                    val silence = ShortArray(1_600)
                    repeat(30) {
                        val endpoint = recognizer.acceptWaveForm(silence, silence.size)
                        val result = if (endpoint) recognizer.result else recognizer.partialResult
                        val text = JSONObject(result).optString(if (endpoint) "text" else "partial")
                        assertEquals(0, BarkWordMatcher.countBarks(text))
                    }
                    assertEquals(0, BarkWordMatcher.countBarks(JSONObject(recognizer.finalResult).optString("text")))
                }
            }
        }
    }
}
