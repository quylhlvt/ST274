package com.oc.catemoji.catoc.data.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Recognizer

/** Run manually with -e barkMicDiagnostics true after the debug capture activity. */
@RunWith(AndroidJUnit4::class)
class BarkMicrophoneDiagnosticsTest {
    @Test fun compareCapturedMicrophone() = runBlocking<Unit>(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("barkMicDiagnostics") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "bark-diagnostics")
        val speechOnly = InstrumentationRegistry.getArguments().getString("speechOnly") == "true"
        val pcm = File(directory, if (speechOnly) "speech-only.pcm" else "live.pcm").readBytes()
        val samples = ShortArray(pcm.size / 2) { index ->
            ((pcm[index * 2].toInt() and 255) or (pcm[index * 2 + 1].toInt() shl 8)).toShort()
        }
        val grid = InstrumentationRegistry.getArguments().getString("barkGridDiagnostics") == "true"
        val rolling = InstrumentationRegistry.getArguments().getString("barkRollingDiagnostics") == "true"
        val openFast = InstrumentationRegistry.getArguments().getString("barkOpenFastDiagnostics") == "true"
        val modes = when {
            openFast -> listOf("openFastProcessed")
            rolling -> listOf("adaptive800q3max1200", "adaptive800q3max1600", "adaptive800q3")
            grid -> listOf("currentRaw", "restrictedProcessed", "rejectUnknownProcessed")
            else -> listOf("current", "restricted", "rejectUnknown", "open")
        }
        val reports = JSONArray()
        val foreground = ActivityScenario.launch<BarkMicrophoneDiagnosticsActivity>(
            Intent(context, BarkMicrophoneDiagnosticsActivity::class.java))
        try {
        VoskVietnameseModel.open(context).use { model ->
            modes.forEach { mode ->
                val recognizer = when (mode) {
                    "current", "currentRaw", "adaptive800", "adaptive800q3", "adaptive800q3max1200", "adaptive800q3max1600", "adaptive800p10", "adaptive800q3p10", "adaptive1200", "rolling400", "rolling600", "rolling800", "rolling1000", "rolling1200", "rolling2000", "rolling3000" -> Recognizer(model, 16_000f, SpeechToTextManager.GRAMMAR)
                    "restricted", "restrictedProcessed" -> Recognizer(model, 16_000f, "[\"gâu\",\"go\",\"gô\"]")
                    "rejectUnknown", "rejectUnknownProcessed" -> Recognizer(model, 16_000f, "[\"gâu\",\"go\",\"gô\",\"[unk]\"]")
                    else -> Recognizer(model, 16_000f)
                }
                recognizer.use {
                    it.setWords(true)
                    it.setPartialWords(true)
                    val events = JSONArray()
                    val counter = BarkSpeechCounter()
                    val processor = PcmSpeechProcessor(16_000)
                    var previous = ""
                    var sinceReset = 0
                    var peak = 0f
                    var quietBlocks = 0
                    val block = ShortArray(640)
                    for (offset in 0 until samples.size + 32_000 step block.size) {
                        block.fill(0)
                        if (offset < samples.size) samples.copyInto(block, 0, offset, minOf(offset + block.size, samples.size))
                        val level = if (mode == "current" || mode.startsWith("adaptive") || mode.startsWith("rolling") || mode.endsWith("Processed")) processor.process(block, block.size) else 0f
                        val final = it.acceptWaveForm(block, block.size)
                        val livePartial = mode == "current" || rolling || grid || openFast
                        val raw = if (livePartial) null else if (final) it.result else it.partialResult
                        val update = if (livePartial) SpeechToTextManager.readUpdate(it, final, 0f)
                            else SpeechToTextManager.parseResult(checkNotNull(raw), final, 0f)
                        val before = counter.count
                        counter.accept(update.text, final, update.words, update.partialText)
                        val text = update.partialText ?: update.text
                        if (text != previous || final || before != counter.count) {
                            val result = if (raw != null) JSONObject(raw)
                                else if (final) JSONObject().put("text", update.text)
                                else JSONObject(it.partialResult)
                            events.put(JSONObject().put("at", offset / 16_000.0).put("final", final)
                                .put("result", result).put("fast", text).put("points", counter.count))
                        }
                        previous = if (final) "" else text
                        sinceReset += block.size
                        peak = maxOf(peak, level)
                        val quietRatio = if (mode.endsWith("p10")) 0.10f else 0.18f
                        quietBlocks = if (level < maxOf(0.012f, peak * quietRatio)) quietBlocks + 1 else 0
                        val windowMs = when (mode) {
                            "adaptive800" -> 800
                            "adaptive800q3", "adaptive800q3max1200", "adaptive800q3max1600", "adaptive800p10", "adaptive800q3p10" -> 800
                            "adaptive1200" -> 1200
                            "rolling400" -> 400
                            "rolling600" -> 600
                            "rolling800" -> 800
                            "rolling1000" -> 1000
                            "rolling1200" -> 1200
                            "rolling2000" -> 2000
                            "rolling3000" -> 3000
                            else -> 0
                        }
                        val maxMs = when {
                            mode.contains("max1200") -> 1200
                            mode.contains("max1600") -> 1600
                            else -> 2000
                        }
                        val shouldFlush = if (mode.startsWith("adaptive"))
                            sinceReset >= maxMs * 16 || (sinceReset >= windowMs * 16 && quietBlocks >= (if (mode.contains("q3")) 3 else 2))
                        else windowMs > 0 && (offset + block.size) % (windowMs * 16) == 0
                        if (shouldFlush) {
                            val forced = SpeechToTextManager.parseResult(it.finalResult, true, 0f)
                            val beforeForced = counter.count
                            counter.accept(forced.text, true, forced.words)
                            events.put(JSONObject().put("at", (offset + block.size) / 16_000.0)
                                .put("forced", true).put("text", forced.text)
                                .put("added", counter.count - beforeForced).put("points", counter.count))
                            it.reset()
                            previous = ""
                            sinceReset = 0
                            peak = 0f
                            quietBlocks = 0
                        }
                    }
                    events.put(JSONObject().put("final", true).put("result", JSONObject(it.finalResult)))
                    reports.put(JSONObject().put("mode", mode).put("events", events))
                }
            }
        }
        val report = when {
            openFast -> "report-open-fast.json"
            rolling -> "report-rolling.json"
            grid -> "report-grid.json"
            speechOnly -> "report-current-speech-only.json"
            else -> "report-current.json"
        }
        File(directory, report).writeText(reports.toString(2))
        } finally { foreground.close() }
    }
}
