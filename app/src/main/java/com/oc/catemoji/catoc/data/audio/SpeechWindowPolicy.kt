package com.oc.catemoji.catoc.data.audio

/** Ends a recognition window at an acoustic gap, never in the middle of a held sound. */
internal class SpeechWindowPolicy(sampleRate: Int) {
    init {
        require(sampleRate > 0)
    }

    private val foregroundSamplesRequired = samplesForMillis(sampleRate, 80)
    private val quietSamplesRequired = samplesForMillis(sampleRate, 80)
    private var foregroundSamples = 0
    private var quietSamples = 0
    private var peak = 0f

    /** The voiced gate already confirmed the onset, including the audio in its pre-roll. */
    fun onVoicedOnset() {
        foregroundSamples = foregroundSamplesRequired
    }

    /** [level] is conditioned PCM RMS; [sampleCount] is the actual read size. */
    fun accept(level: Float, sampleCount: Int): Boolean {
        require(sampleCount >= 0)
        if (sampleCount == 0 || !level.isFinite() || level < 0f) return false

        peak = maxOf(peak, level)
        val quietLevel = maxOf(MIN_QUIET_LEVEL, peak * QUIET_PEAK_RATIO)
        if (level <= quietLevel) {
            quietSamples += minOf(sampleCount, quietSamplesRequired - quietSamples)
            if (quietSamples == quietSamplesRequired) {
                val shouldFlush = foregroundSamples == foregroundSamplesRequired
                // Also discard unarmed clicks so they cannot accumulate across silence.
                reset()
                return shouldFlush
            }
        } else {
            quietSamples = 0
            if (level >= MIN_FOREGROUND_LEVEL) {
                foregroundSamples += minOf(sampleCount, foregroundSamplesRequired - foregroundSamples)
            }
        }
        return false
    }

    /** Call when the recognizer supplies its own endpoint, so its next phrase starts cleanly. */
    fun reset() {
        foregroundSamples = 0
        quietSamples = 0
        peak = 0f
    }

    private companion object {
        // A quiet bark around 0.01 RMS can arm immediately, without noise calibration.
        const val MIN_FOREGROUND_LEVEL = 0.006f
        const val MIN_QUIET_LEVEL = 0.001f
        const val QUIET_PEAK_RATIO = 0.18f

        fun samplesForMillis(sampleRate: Int, millis: Int): Int =
            ((sampleRate.toLong() * millis + 999) / 1_000).toInt()
    }
}
