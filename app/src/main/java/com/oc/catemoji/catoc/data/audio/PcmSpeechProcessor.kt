package com.oc.catemoji.catoc.data.audio

import kotlin.math.exp
import kotlin.math.sqrt

/** Session-local conditioning. Never gates a quiet bark or converts loudness into points. */
internal class PcmSpeechProcessor(private val sampleRate: Int) {
    private val noiseSuppressor = PcmNoiseSuppressor()
    // Remove DC and low-frequency handling/wind noise without clipping bark onsets.
    private val highPass = exp(-2.0 * Math.PI * 80.0 / sampleRate)
    private var previousInput = 0.0
    private var previousOutput = 0.0
    private var gain = 1.0
    private var noiseFloor: Double? = null

    /** Modifies only the samples actually returned by AudioRecord; returns the real input RMS. */
    fun process(pcm: ShortArray, count: Int): Float {
        require(count in 0..pcm.size)
        if (count == 0) return 0f
        var inputPower = 0.0
        for (index in 0 until count) inputPower += pcm[index].toDouble() * pcm[index]
        for (index in 0 until count) {
            val input = pcm[index].toDouble()
            val output = highPass * (previousOutput + input - previousInput)
            previousInput = input
            previousOutput = output
            pcm[index] = output.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        // Remove rumble before estimating the spectrum, so DC cannot modulate the noise profile.
        noiseSuppressor.process(pcm, count)
        var filteredPower = 0.0
        for (index in 0 until count) filteredPower += pcm[index].toDouble() * pcm[index]
        val rms = sqrt(filteredPower / count) / 32768.0
        val inputRms = sqrt(inputPower / count) / 32768.0
        val floor = noiseFloor ?: rms
        val noiseAdaptation = 1.0 - exp(-count.toDouble() / (sampleRate * 8.0))
        noiseFloor = if (rms < floor) rms else floor + (rms - floor) * noiseAdaptation
        // Only boost foreground above the slowly tracked floor, never steady background noise.
        // A block dominated by the rejected rumble/DC must not be amplified back up.
        val target = if (rms >= 0.008 && rms > floor * 1.8 && rms >= inputRms * 0.5)
            (0.10 / rms).coerceIn(1.0, 3.0) else 1.0
        val smoothing = 1.0 - exp(-count.toDouble() / (sampleRate * 0.12))
        val nextGain = gain + (target - gain) * smoothing
        for (index in 0 until count) {
            val sampleGain = gain + (nextGain - gain) * (index + 1) / count
            pcm[index] = (pcm[index] * sampleGain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        gain = nextGain
        return inputRms.toFloat()
    }
}
