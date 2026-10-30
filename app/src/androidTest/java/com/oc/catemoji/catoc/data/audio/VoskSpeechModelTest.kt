package com.oc.catemoji.catoc.data.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Recognizer

@RunWith(AndroidJUnit4::class)
class VoskSpeechModelTest {
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
