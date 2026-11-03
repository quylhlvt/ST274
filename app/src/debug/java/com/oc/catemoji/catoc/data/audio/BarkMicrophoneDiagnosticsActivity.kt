package com.oc.catemoji.catoc.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.oc.catemoji.catoc.R
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manual, debug-only, bounded capture; never updates a profile or battle score. */
class BarkMicrophoneDiagnosticsActivity : ComponentActivity() {
    private lateinit var status: TextView
    private var capture: Job? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCapture()
        else status.text = getString(R.string.debug_bark_error, "Microphone permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 24f
            text = getString(R.string.debug_bark_quiet, 4)
        }
        setContentView(status)
        if (!intent.getBooleanExtra("capture", false)) {
            status.setText(R.string.debug_bark_done)
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startCapture()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startCapture() {
        capture = lifecycleScope.launch {
            try {
                check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                recordSample()
                status.setText(R.string.debug_bark_done)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status.text = getString(R.string.debug_bark_error, error.message)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordSample() = withContext(Dispatchers.IO) {
        val rate = SpeechToTextManager.SAMPLE_RATE
        val speechOnly = intent.getBooleanExtra("speechOnly", false)
        val duration = if (speechOnly) 18 else 30
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(size > 0)
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(size, 4_096))
        val directory = File(filesDir, "bark-diagnostics").apply { mkdirs() }
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            File(directory, if (speechOnly) "speech-only.pcm" else "live.pcm").outputStream().buffered().use { output ->
                val samples = ShortArray(640)
                val bytes = ByteArray(samples.size * 2)
                var total = 0
                var previousSecond = -1
                while (total < rate * duration) {
                    ensureActive()
                    val second = total / rate
                    if (second != previousSecond) {
                        previousSecond = second
                        val (label, end) = when {
                            second < 4 -> R.string.debug_bark_quiet to 4
                            second < 16 -> R.string.debug_bark_speech to 16
                            !speechOnly && second < 28 -> R.string.debug_bark_barks to 28
                            else -> R.string.debug_bark_quiet to duration
                        }
                        withContext(Dispatchers.Main) { status.text = getString(label, end - second) }
                    }
                    val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    check(count > 0) { "AudioRecord.read=$count" }
                    for (index in 0 until count) {
                        bytes[index * 2] = samples[index].toByte()
                        bytes[index * 2 + 1] = (samples[index].toInt() shr 8).toByte()
                    }
                    output.write(bytes, 0, count * 2)
                    total += count
                }
            }
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } finally { recorder.release() }
        }
    }

    override fun onStop() {
        capture?.cancel()
        super.onStop()
    }
}
