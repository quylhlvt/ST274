package com.oc.catemoji.catoc.data.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Streaming spectral suppression for stationary noise, including devices without a native effect. */
internal class PcmNoiseSuppressor {
    private val input = DoubleArray(SIZE)
    private val output = DoubleArray(HOP)
    private val overlap = DoubleArray(HOP)
    private val real = DoubleArray(SIZE)
    private val imaginary = DoubleArray(SIZE)
    private val noise = DoubleArray(SIZE)
    private val smoothedPower = DoubleArray(SIZE)
    private val window = DoubleArray(SIZE) { sqrt(0.5 - 0.5 * cos(2 * PI * (it + 0.5) / SIZE)) }
    private var position = 0
    private var initialized = false

    /** In place, continuous across arbitrary short reads. Latency is one 256-sample frame. */
    fun process(pcm: ShortArray, count: Int) {
        require(count in 0..pcm.size)
        for (i in 0 until count) {
            input[HOP + position] = pcm[i].toDouble()
            pcm[i] = output[position].toInt().coerceIn(-32768, 32767).toShort()
            if (++position == HOP) {
                analyze()
                input.copyInto(input, 0, HOP, SIZE)
                position = 0
            }
        }
    }

    private fun analyze() {
        for (i in 0 until SIZE) {
            real[i] = input[i] * window[i]
            imaginary[i] = 0.0
        }
        fft(inverse = false)
        // A minimum tracker falls quickly when noise subsides and rises slowly, so a
        // short voiced bark does not become the noise profile. No calibration pause is required.
        for (i in 0 until SIZE) {
            val power = real[i] * real[i] + imaginary[i] * imaginary[i]
            smoothedPower[i] = if (initialized) 0.8 * smoothedPower[i] + 0.2 * power else power
            noise[i] = if (initialized) minOf(smoothedPower[i],
                maxOf(smoothedPower[i] * 0.01, noise[i] * 1.015 + 1e-6)) else power
            // Keep a soft floor instead of blanking quiet consonants or bark onsets.
            val gain = sqrt((1.0 - 1.4 * noise[i] / (power + 1e-6)).coerceIn(0.12, 1.0))
            real[i] *= gain
            imaginary[i] *= gain
        }
        initialized = true
        fft(inverse = true)
        for (i in 0 until HOP) {
            output[i] = overlap[i] + real[i] * window[i]
            overlap[i] = real[i + HOP] * window[i + HOP]
        }
    }

    private fun fft(inverse: Boolean) {
        var reversed = 0
        for (i in 1 until SIZE) {
            var bit = SIZE shr 1
            while ((reversed and bit) != 0) {
                reversed = reversed xor bit
                bit = bit shr 1
            }
            reversed = reversed xor bit
            if (i < reversed) {
                val r = real[i]
                real[i] = real[reversed]
                real[reversed] = r
                val im = imaginary[i]
                imaginary[i] = imaginary[reversed]
                imaginary[reversed] = im
            }
        }
        var length = 2
        while (length <= SIZE) {
            val angle = (if (inverse) 2 else -2) * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            for (start in 0 until SIZE step length) {
                var rotationReal = 1.0
                var rotationImaginary = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * rotationReal - imaginary[odd] * rotationImaginary
                    val oddImaginary = real[odd] * rotationImaginary + imaginary[odd] * rotationReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextRotation = rotationReal * stepReal - rotationImaginary * stepImaginary
                    rotationImaginary = rotationReal * stepImaginary + rotationImaginary * stepReal
                    rotationReal = nextRotation
                }
            }
            length *= 2
        }
        if (inverse) for (i in 0 until SIZE) {
            real[i] /= SIZE
            imaginary[i] /= SIZE
        }
    }

    companion object {
        const val SIZE = 256
        private const val HOP = SIZE / 2
    }
}
