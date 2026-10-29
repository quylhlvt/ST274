package com.oc.catemoji.catoc.data.audio

import android.util.Log
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
    private val minimumModelScore: Float = 0.75f
) {
    data class Burst(val startMs: Long, val endMs: Long)
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
    private var burstStartMs = 0L
    private var lastBurstEndMs = -10000L
    private var points = 0

    init {
        require(sampleRate > 0 && sampleRate % 50 == 0)
        require(minimumModelScore in 0f..1f)
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
                processFrame(rms, frameIndex * 20L)
                frameCount = 0
                framePower = 0.0
                frameIndex++
            }
        }
    }

    private fun processFrame(rms: Float, frameStartMs: Long) {
        val onset = max(0.012f, noiseFloor * 3f)
        val release = max(0.007f, noiseFloor * 1.6f)
        if (!active) {
            // Adapt ambient-noise estimate only on quiet frames.
            if (rms < onset) {
                noiseFloor = noiseFloor * 0.98f + rms * 0.02f
            }
            loudFrames = if (rms >= onset) loudFrames + 1 else 0
            if (loudFrames >= 2 && frameStartMs - lastBurstEndMs >= 60L) {
                active = true
                burstStartMs = max(0L, frameStartMs - 20L)
                quietFrames = 0
                loudFrames = 0
            }
            return
        }

        quietFrames = if (rms < release) quietFrames + 1 else 0
        // A 40ms valley separates adjacent "gâu" syllables.
        if (quietFrames >= 2) {
            val endMs = frameStartMs - 20L
            val durationMs = endMs - burstStartMs
            Log.d(TAG, "burst ${burstStartMs}-${endMs}ms dur=$durationMs ok=${durationMs in 80L..900L} noise=${"%.4f".format(noiseFloor)}")
            if (durationMs in 80L..900L) {
                pending.addLast(Burst(burstStartMs, endMs))
                while (pending.size > 32) pending.removeFirst()
            }
            lastBurstEndMs = endMs
            active = false
            quietFrames = 0
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
            if (covered && barkScore >= minimumModelScore) {
                it.remove()
                added++
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
        burstStartMs = 0
        lastBurstEndMs = -10000L
        points = 0
    }
    private companion object { const val TAG = "BarkTracker" }
}
