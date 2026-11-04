package com.oc.catemoji.catoc.data.audio

/** Keeps a decoder idle through broadband noise, retaining PCM before a voiced onset. */
internal class PcmSpeechGate(sampleRate: Int, private val blockSamples: Int) {
    init {
        require(sampleRate > 0)
        require(blockSamples > 0)
    }

    private val preRoll = ShortArray(samplesForMillis(sampleRate, 200))
    private val analysis = ShortArray(samplesForMillis(sampleRate, 40))
    private val minLag = maxOf(1, (sampleRate.toLong() + 499).div(500).toInt())
    private val maxLag = minOf(sampleRate / 80, analysis.size - 1)
    private val lagStep = maxOf(1, sampleRate / 8_000)
    private var historyCount = 0
    private var historyWrite = 0
    private var analysisCount = 0
    private var active = false
    val isOpen: Boolean get() = active

    /** Reused output. Only the sample count returned by [process] is valid. */
    val buffer = ShortArray(preRoll.size + blockSamples)

    /** Input is conditioned PCM. Waiting never changes the caller's samples. */
    fun process(pcm: ShortArray, count: Int): Int {
        require(count in 0..minOf(pcm.size, blockSamples))
        if (count == 0) return 0
        if (active) {
            pcm.copyInto(buffer, endIndex = count)
            return count
        }

        updateAnalysis(pcm, count)
        if (analysisCount == analysis.size && hasVoicedActivity()) {
            val oldest = if (historyCount == preRoll.size) historyWrite else 0
            val firstCount = minOf(historyCount, preRoll.size - oldest)
            preRoll.copyInto(buffer, 0, oldest, oldest + firstCount)
            preRoll.copyInto(buffer, firstCount, 0, historyCount - firstCount)
            pcm.copyInto(buffer, historyCount, 0, count)
            active = true
            return historyCount + count
        }

        for (index in 0 until count) {
            preRoll[historyWrite] = pcm[index]
            historyWrite = (historyWrite + 1) % preRoll.size
        }
        historyCount += minOf(count, preRoll.size - historyCount)
        return 0
    }

    /** Close after either an acoustic gap or a recognizer endpoint. */
    fun reset() {
        active = false
        historyCount = 0
        historyWrite = 0
        analysisCount = 0
        preRoll.fill(0)
        analysis.fill(0)
        buffer.fill(0)
    }

    private fun updateAnalysis(pcm: ShortArray, count: Int) {
        if (count >= analysis.size) {
            pcm.copyInto(analysis, 0, count - analysis.size, count)
            analysisCount = analysis.size
        } else {
            val retained = minOf(analysisCount, analysis.size - count)
            analysis.copyInto(analysis, 0, analysisCount - retained, analysisCount)
            pcm.copyInto(analysis, retained, 0, count)
            analysisCount = retained + count
        }
    }

    private fun hasVoicedActivity(): Boolean {
        var sum = 0.0
        for (sample in analysis) sum += sample
        val mean = sum / analysis.size
        var energy = 0.0
        for (sample in analysis) {
            val centered = sample - mean
            energy += centered * centered
        }
        // Centering also prevents a DC offset from looking like perfectly periodic speech.
        if (energy < analysis.size * MIN_RMS_PCM * MIN_RMS_PCM) return false

        for (lag in minLag..maxLag step lagStep) {
            var correlation = 0.0
            var earlierEnergy = 0.0
            var laterEnergy = 0.0
            for (index in lag until analysis.size) {
                val earlier = analysis[index - lag] - mean
                val later = analysis[index] - mean
                correlation += earlier * later
                earlierEnergy += earlier * earlier
                laterEnergy += later * later
            }
            // Squared comparison avoids a square root for every candidate pitch lag.
            if (correlation > 0.0 && correlation * correlation >=
                MIN_CORRELATION_SQUARED * earlierEnergy * laterEnergy) return true
        }
        return false
    }

    private companion object {
        const val MIN_RMS_PCM = 0.002 * 32768.0
        const val MIN_CORRELATION_SQUARED = 0.45 * 0.45

        fun samplesForMillis(sampleRate: Int, millis: Int): Int =
            maxOf(1, ((sampleRate.toLong() * millis + 999) / 1_000).toInt())
    }
}
