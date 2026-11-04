package com.oc.catemoji.catoc.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.io.Closeable

/** One live device microphone. All resources, including optional effects, share its lifetime. */
internal class DeviceMicrophone private constructor(val recorder: AudioRecord) : Closeable {
    private val effects = mutableListOf<AudioEffect>()

    private fun enableEffect(create: () -> AudioEffect?) {
        // OEMs may advertise an effect but fail to create/enable it. Recording must still work.
        try {
            val effect = create() ?: return
            effects.add(effect)
            if (effect.setEnabled(true) != AudioEffect.SUCCESS) {
                Log.w(TAG, "Microphone preprocessing unavailable")
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Microphone preprocessing unavailable", error)
        }
    }

    override fun close() {
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } finally {
            effects.forEach { effect -> runCatching { effect.release() } }
            effects.clear()
            recorder.release()
        }
    }

    companion object {
        private const val TAG = "DeviceMicrophone"

        @SuppressLint("MissingPermission")
        fun open(context: Context, sampleRate: Int, blockSamples: Int): DeviceMicrophone {
            val minimumBytes = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minimumBytes > 0) { "PCM16 microphone unavailable at $sampleRate Hz" }
            val microphone = DeviceMicrophone(AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimumBytes, blockSamples * Short.SIZE_BYTES * 4),
            ))
            try {
                val recorder = microphone.recorder
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Cannot initialize microphone" }
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val builtInMicrophones = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                // Both the primary and rear microphones have TYPE_BUILTIN_MIC. OEM
                // ordering is not a preference: Realme can list a silent rear input first.
                val builtIn = builtInMicrophones.firstOrNull { it.address.equals("bottom", ignoreCase = true) }
                    ?: builtInMicrophones.firstOrNull { !it.address.equals("back", ignoreCase = true) }
                // Keep capture on the handset even when headphones/Bluetooth are connected.
                if (builtIn != null) check(recorder.setPreferredDevice(builtIn)) { "Cannot select device microphone" }
                if (NoiseSuppressor.isAvailable()) {
                    microphone.enableEffect { NoiseSuppressor.create(recorder.audioSessionId) }
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    microphone.enableEffect { AcousticEchoCanceler.create(recorder.audioSessionId) }
                }
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Cannot start microphone" }
                Log.d(TAG, "Live input type=${recorder.routedDevice?.type}, " +
                    "address=${recorder.routedDevice?.address}, rate=${recorder.sampleRate}")
                return microphone
            } catch (error: Throwable) {
                microphone.close()
                throw error
            }
        }
    }
}
