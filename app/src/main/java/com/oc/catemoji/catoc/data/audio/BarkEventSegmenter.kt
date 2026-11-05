package com.oc.catemoji.catoc.data.audio

import kotlin.math.sqrt

/** Finds short sound events. The classifier decides which events are barks. */
internal class BarkEventSegmenter {
    private val previous = ShortArray(BLOCK_SAMPLES)
    private var previousCount = 0
    private var noiseLevel = 0.001f
    private var peak = 0f
    private var activeSamples = 0
    private var durationSamples = 0
    private var quietSamples = 0
    private var length = 0
    private var eventLength = 0
    private var prevLevel = 0f
    /** Reused event PCM. Only the count returned by [accept] is valid. */
    val event = ShortArray(MAX_EVENT_SAMPLES + BLOCK_SAMPLES)
    var level = 0f
        private set

    private fun reset(pcm: ShortArray, count: Int) {
        peak = 0f
        activeSamples = 0
        durationSamples = 0
        quietSamples = 0
        length = 0
        eventLength = 0
        pcm.copyInto(previous, endIndex = count)
        previousCount = count
    }

    fun accept(pcm: ShortArray, count: Int): Int {
        require(count in 0..minOf(BLOCK_SAMPLES, pcm.size))
        if (count == 0) return 0
        var power = 0.0
        for (index in 0 until count) power += pcm[index].toDouble() * pcm[index]
        level = (sqrt(power / count) / 32768.0).toFloat()

        if (activeSamples == 0) {
            if (level < maxOf(MIN_ONSET, noiseLevel * 3f)) {
                noiseLevel += (level - noiseLevel) * 0.1f
                pcm.copyInto(previous, endIndex = count)
                previousCount = count
                return 0
            }
            previous.copyInto(event, endIndex = previousCount)
            length = previousCount
        }

        // Onset mới (level nhảy vọt sau khi đã giảm): cắt event cũ, tiếng mới bắt đầu lại
        val newOnset = activeSamples >= MIN_EVENT_SAMPLES &&
                level > prevLevel * 2.2f && level > peak * 0.5f && quietSamples > 0
        prevLevel = level
        if (newOnset) {
            val result = eventLength
            reset(pcm, count)
            // block hiện tại là đầu tiếng mới: bắt đầu event mới ngay
            pcm.copyInto(event, 0, endIndex = count)
            length = count
            activeSamples = count
            peak = level
            quietSamples = 0
            durationSamples = count
            eventLength = count
            return result
        }

        peak = maxOf(peak, level)
        if (activeSamples > MAX_EVENT_SAMPLES) noiseLevel += (level - noiseLevel) * 0.035f
        quietSamples = if (level < maxOf(noiseLevel * 1.7f, peak * 0.15f)) quietSamples + count else 0
        activeSamples += minOf(count, MAX_EVENT_SAMPLES + 1 - activeSamples)
        val copied = minOf(count, event.size - length)
        pcm.copyInto(event, length, endIndex = copied)
        length += copied
        if (quietSamples == 0) {
            durationSamples = activeSamples
            eventLength = length
        }

        if (activeSamples > MAX_EVENT_SAMPLES) {
            val result = eventLength
            reset(pcm, count)
            return result
        }

        if (quietSamples < GAP_SAMPLES) return 0

        val result = if (durationSamples in MIN_EVENT_SAMPLES..MAX_EVENT_SAMPLES) eventLength else 0
        reset(pcm, count)
        return result
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val BLOCK_SAMPLES = 320
        private const val GAP_SAMPLES = 960
        private const val MIN_EVENT_SAMPLES = 1_280
        private const val MAX_EVENT_SAMPLES = 10_400
        private const val MIN_ONSET = 0.003f
    }
}