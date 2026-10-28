package com.oc.catemoji.catoc.data.audio

/** Counts a new sound burst, not every overlapping model inference window. */
class BarkEventCounter(
    private val quietGapMs: Long = 50L,
    private val minimumEventGapMs: Long = 200L,
) {
    var count: Int = 0
        private set
    private var inBurst = false
    private var quietSince: Long? = null
    private var lastEventAt: Long? = null

    fun update(isBark: Boolean, nowMs: Long): Int {
        if (isBark) {
            val previous = lastEventAt
            if (!inBurst) {
                if (previous == null || nowMs - previous >= minimumEventGapMs) {
                    if (count < Int.MAX_VALUE) count++
                    lastEventAt = nowMs
                }
                inBurst = true
            }
            quietSince = null
        } else {
            val start = quietSince ?: nowMs.also { quietSince = it }
            if (nowMs - start >= quietGapMs) inBurst = false
        }
        return count
    }

    fun endBurst() {
        inBurst = false
        quietSince = null
    }
}
