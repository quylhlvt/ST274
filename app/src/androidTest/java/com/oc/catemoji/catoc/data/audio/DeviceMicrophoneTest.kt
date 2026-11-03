package com.oc.catemoji.catoc.data.audio

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioRecord
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceMicrophoneTest {
    @Test fun readsBuiltInMicrophoneAndReopensAfterCancellation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
            .use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        ActivityScenario.launch<BarkMicrophoneDiagnosticsActivity>(
            Intent(context, BarkMicrophoneDiagnosticsActivity::class.java),
        ).use {
            DeviceMicrophone.open(context, 16_000, 640).use { mic ->
                assertEquals(AudioDeviceInfo.TYPE_BUILTIN_MIC, mic.recorder.preferredDevice?.type)
                assertEquals(AudioRecord.RECORDSTATE_RECORDING, mic.recorder.recordingState)
                val pcm = ShortArray(640)
                assertTrue(mic.recorder.read(pcm, 0, pcm.size) > 0)
                assertEquals(AudioDeviceInfo.TYPE_BUILTIN_MIC, mic.recorder.routedDevice?.type)
                assertEquals(16_000, mic.recorder.sampleRate)
            }
            // take cancels the live flow. A second session must acquire and release the same mic.
            repeat(2) {
                val updates = withTimeout(30_000) { SpeechToTextManager(context).listen().take(8).toList() }
                assertEquals(8, updates.size)
                assertTrue(updates.all { it.level.isFinite() && it.level in 0f..1f })
            }
        }
    }
}
