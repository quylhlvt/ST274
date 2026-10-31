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

@RunWith(AndroidJUnit4::class)
class VoskSpeechModelTest {
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
