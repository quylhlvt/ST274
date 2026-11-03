package com.oc.catemoji.catoc.data.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PcmNoiseSuppressorTest {
    @Test fun reducesStationaryNoiseWithoutRemovingForegroundVoiceBand() {
        val random = Random(274)
        val reference = ShortArray(48_000) { i ->
            if (i < 16_000) 0 else (6000 * sin(2 * PI * 500 * i / 16_000) +
                2500 * sin(2 * PI * 1500 * i / 16_000)).toInt().toShort()
        }
        val noisy = ShortArray(reference.size) { i ->
            (reference[i] + random.nextInt(-3000, 3001)).toShort()
        }
        val output = noisy.copyOf()
        val suppressor = PcmNoiseSuppressor()
        suppressor.process(output, output.size)
        val latency = PcmNoiseSuppressor.SIZE
        fun snr(processed: Boolean): Double {
            var signal = 0.0
            var error = 0.0
            for (i in 20_000 until 30_000) {
                val value = if (processed) output[i + latency].toDouble() else noisy[i].toDouble()
                signal += reference[i].toDouble() * reference[i]
                error += (value - reference[i]) * (value - reference[i])
            }
            return 10 * log10(signal / error)
        }
        assertTrue("SNR must improve by at least 3 dB: ${snr(false)} -> ${snr(true)}", snr(true) > snr(false) + 3)
        val before = noisy.sliceArray(8000 until 15_000).sumOf { it.toDouble() * it }
        val after = output.sliceArray(8000 until 15_000).sumOf { it.toDouble() * it }
        assertTrue("Steady background energy should fall", after < before * 0.6)
    }

    @Test fun immediateVoiceRemainsAudibleWithoutAQuietCalibrationPeriod() {
        val input = ShortArray(8000) { (6000 * sin(2 * PI * 500 * it / 16_000)).toInt().toShort() }
        val output = input.copyOf()
        PcmNoiseSuppressor().process(output, output.size)
        val energy = output.drop(PcmNoiseSuppressor.SIZE).sumOf { it.toDouble() * it }
        val original = input.drop(PcmNoiseSuppressor.SIZE).sumOf { it.toDouble() * it }
        assertTrue(energy > original * 0.1)
    }
}
