package com.oc.catemoji.catoc.data.audio

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YamnetMicrophoneDiagnosticsTest {
    @Test fun compareCapturedMicrophoneAndSpeechFixtures() {
        val custom = InstrumentationRegistry.getArguments().getString("customBarkDiagnostics") == "true"
        assumeTrue(custom || InstrumentationRegistry.getArguments().getString("barkYamnetDiagnostics") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Keep this device's power manager from freezing native inference in the background.
        ActivityScenario.launch<BarkMicrophoneDiagnosticsActivity>(
            Intent(context, BarkMicrophoneDiagnosticsActivity::class.java),
        ).use {
            val directory = File(context.filesDir, "bark-diagnostics")
            val reports = JSONArray()
            YamnetDiagnosticModel(context, custom).use { model ->
                val sources = mutableMapOf("live" to File(directory, "live.pcm").readBytes())
                if (File(directory, "speech-only.pcm").isFile) sources["speech-only"] = File(directory, "speech-only.pcm").readBytes()
                listOf("gau", "go", "mo", "vang", "giang", "greeting", "counting", "weather", "near-bark", "gau-repeat").forEach {
                    sources[it] = instrumentation.context.assets.open("bark-vn/$it.pcm").use { it.readBytes() }
                }
                sources.forEach { (name, bytes) ->
                    val rawPcm = ShortArray(bytes.size / 2) { index ->
                        ((bytes[index * 2].toInt() and 255) or (bytes[index * 2 + 1].toInt() shl 8)).toShort()
                    }
                    val rate = if (custom) 44_100 else 16_000
                    val pcm = if (!custom) rawPcm else ShortArray((rawPcm.size.toLong() * rate / 16_000).toInt()) { index ->
                        val source = index * 16_000.0 / rate
                        val before = source.toInt().coerceAtMost(rawPcm.lastIndex)
                        val after = (before + 1).coerceAtMost(rawPcm.lastIndex)
                        (rawPcm[before] + (rawPcm[after] - rawPcm[before]) * (source - before)).toInt().toShort()
                    }
                    val events = JSONArray()
                    val window = ShortArray(model.inputSamples)
                    val started = SystemClock.elapsedRealtime()
                    for (offset in 0 until pcm.size step rate / 5) {
                        window.fill(0)
                        pcm.copyInto(window, 0, offset, minOf(offset + window.size, pcm.size))
                        val scores = model.classify(window)
                        val top = scores.indices.sortedByDescending { scores[it] }.take(8)
                        val labels = JSONArray()
                        top.forEach { labels.put(JSONObject().put("label", model.labels[it]).put("score", scores[it].toDouble())) }
                        events.put(JSONObject().put("at", offset.toDouble() / rate).put("top", labels))
                    }
                    reports.put(JSONObject().put("source", name).put("inputSamples", model.inputSamples)
                        .put("elapsedMs", SystemClock.elapsedRealtime() - started).put("events", events))
                    File(directory, if (custom) "custom-report.json" else "yamnet-report.json").writeText(reports.toString(2))
                }
            }
        }
    }
}
