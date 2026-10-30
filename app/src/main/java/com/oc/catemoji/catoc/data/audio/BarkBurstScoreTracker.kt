package com.oc.catemoji.catoc.data.audio

import kotlin.math.max
import kotlin.math.sqrt
import java.util.ArrayDeque

/**
 * Counts separate bark-like audio bursts, then confirms each burst with a
 * sliding-window classifier result. No microphone or TFLite dependency here.
 *
 * Both PCM frame times and AI window times are measured relative to the
 * beginning of one recording session (not SystemClock.elapsedRealtime()).
 * Call reset() for a new session.
 */
class BarkBurstScoreTracker(
    private val sampleRate: Int = 44_100,
    private val minimumModelScore: Float = 0.85f,
    private val requiredModelMatches: Int = 2,
) {
    private data class Burst(
        val startMs: Long,
        val endMs: Long,
        var matches: Int = 0,
        var lastMatchingWindowEndMs: Long = -1L,
    )
    data class ScoreUpdate(val newPoints: Int, val totalPoints: Int)

    private val frameSamples = sampleRate / 50 // 20ms, requires sampleRate divisible by 50
    private val pending = ArrayDeque<Burst>()

    private var frameCount = 0
    private var framePower = 0.0
    private var frameIndex = 0L
    private var noiseFloor = 0.006f

    private var active = false
    private var loudFrames = 0
    private var quietFrames = 0
    private var candidatePeakRms = 0f
    private var activePeakRms = 0f
    private var burstStartMs = 0L
    private var lastBurstEndMs = -10000L
    private var points = 0
    private var lastPredictionWindowEndMs = -1L

    init {
        require(sampleRate > 0 && sampleRate % 50 == 0)
        require(minimumModelScore in 0f..1f)
        require(requiredModelMatches > 0)
    }

    /** Call from the AudioRecord PCM worker (PCM16 mono). */
    @Synchronized
    fun offerPcm(pcm: ShortArray, count: Int = pcm.size) {
        require(count in 0..pcm.size)
        for (i in 0 until count) {
            val s = pcm[i] / 32768.0
            framePower += s * s
            frameCount++
            if (frameCount == frameSamples) {
                val rms = sqrt(framePower / frameSamples).toFloat()
                processFrame(rms, frameIndex * FRAME_DURATION_MS)
                frameCount = 0
                framePower = 0.0
                frameIndex++
            }
        }
    }

    private fun processFrame(rms: Float, frameStartMs: Long) {
        val onset = max(MINIMUM_ONSET_RMS, noiseFloor * ONSET_NOISE_MULTIPLIER)
        val release = max(MINIMUM_RELEASE_RMS, noiseFloor * RELEASE_NOISE_MULTIPLIER)
        if (!active) {
            // Adapt ambient-noise estimate only on quiet frames.
            if (rms < onset) {
                noiseFloor = noiseFloor * 0.98f + rms * 0.02f
            }
            if (rms >= onset) {
                loudFrames++
                candidatePeakRms = max(candidatePeakRms, rms)
            } else {
                loudFrames = 0
                candidatePeakRms = 0f
            }
            if (loudFrames >= REQUIRED_ONSET_FRAMES &&
                frameStartMs - lastBurstEndMs >= MINIMUM_BURST_GAP_MS) {
                active = true
                burstStartMs = max(0L, frameStartMs - FRAME_DURATION_MS)
                activePeakRms = candidatePeakRms
                quietFrames = 0
                loudFrames = 0
                candidatePeakRms = 0f
            }
            return
        }

        activePeakRms = max(activePeakRms, rms)
        // Echo or background noise may stay above the absolute release level.
        // A clear drop from this burst's own peak still separates two barks.
        val relativeRelease = activePeakRms * RELATIVE_RELEASE_RATIO
        quietFrames = if (rms < max(release, relativeRelease)) quietFrames + 1 else 0
        if (quietFrames >= REQUIRED_RELEASE_FRAMES) {
            val endMs = frameStartMs - FRAME_DURATION_MS
            enqueueBurst(endMs)
            lastBurstEndMs = endMs
            active = false
            quietFrames = 0
            activePeakRms = 0f
            return
        }

        // A sustained sound is not a sequence of separate barks. Wait for
        // an audible valley; never manufacture another point on a timer.
    }

    private fun enqueueBurst(endMs: Long) {
        val durationMs = endMs - burstStartMs
        if (durationMs in MINIMUM_BURST_DURATION_MS..MAXIMUM_BURST_DURATION_MS &&
            activePeakRms >= MINIMUM_PEAK_RMS) {
            pending.addLast(Burst(burstStartMs, endMs))
            while (pending.size > MAX_PENDING_BURSTS) pending.removeFirst()
        }
    }

    /**
     * Offer a classifier result for a sliding PCM window.
     * Example: a 1-second model runs every 200ms; its windows might be
     * [0,1000], [200,1200], [400,1400], etc.
     * Each candidate can score AT MOST ONCE.
     * Return points added by this inference.
     */
    @Synchronized
    fun offerAiPrediction(
        barkScore: Float,
        windowStartMs: Long,
        windowEndMs: Long
    ): ScoreUpdate {
        require(windowEndMs >= windowStartMs)
        // Replaying an inference or delivering it out of order is not a second confirmation.
        if (windowEndMs <= lastPredictionWindowEndMs) return ScoreUpdate(0, points)
        lastPredictionWindowEndMs = windowEndMs
        var added = 0
        val it = pending.iterator()
        while (it.hasNext()) {
            val burst = it.next()
            // Drop old unconfirmed events; do not award guesses.
            if (burst.endMs < windowEndMs - 1800L) {
                it.remove()
                continue
            }
            val covered = burst.startMs >= windowStartMs &&
                burst.endMs <= windowEndMs
            if (covered) {
                if (barkScore.isFinite() && barkScore in minimumModelScore..1f) {
                    if (windowEndMs - burst.lastMatchingWindowEndMs > MAXIMUM_CONFIRMATION_GAP_MS) {
                        burst.matches = 0
                    }
                    burst.lastMatchingWindowEndMs = windowEndMs
                    burst.matches++
                    if (burst.matches >= requiredModelMatches) {
                        it.remove()
                        added++
                    }
                } else {
                    burst.matches = 0
                }
            }
        }
        points += added
        return ScoreUpdate(added, points)
    }

    @Synchronized
    fun currentPoints(): Int = points

    @Synchronized
    fun reset() {
        pending.clear()
        frameCount = 0
        framePower = 0.0
        frameIndex = 0
        noiseFloor = 0.006f
        active = false
        loudFrames = 0
        quietFrames = 0
        candidatePeakRms = 0f
        activePeakRms = 0f
        burstStartMs = 0
        lastBurstEndMs = -10000L
        points = 0
        lastPredictionWindowEndMs = -1L
    }

    private companion object {
        const val FRAME_DURATION_MS = 20L
        const val REQUIRED_ONSET_FRAMES = 2
        const val REQUIRED_RELEASE_FRAMES = 2
        const val MINIMUM_BURST_GAP_MS = 40L
        const val MINIMUM_BURST_DURATION_MS = 80L
        const val MAXIMUM_BURST_DURATION_MS = 600L
        const val MAX_PENDING_BURSTS = 32
        const val MINIMUM_ONSET_RMS = 0.015f
        const val MINIMUM_PEAK_RMS = 0.025f
        const val MINIMUM_RELEASE_RMS = 0.006f
        const val ONSET_NOISE_MULTIPLIER = 2.2f
        const val RELEASE_NOISE_MULTIPLIER = 1.4f
        const val RELATIVE_RELEASE_RATIO = 0.45f
        const val MAXIMUM_CONFIRMATION_GAP_MS = 500L
    }
}
