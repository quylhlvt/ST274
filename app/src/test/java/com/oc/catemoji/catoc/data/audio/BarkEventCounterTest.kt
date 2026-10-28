package com.oc.catemoji.catoc.data.audio

import org.junit.Assert.*
import org.junit.Test

class BarkEventCounterTest {
    @Test fun sustainedBarkingCountsOnceAcrossManyAudioAndInferenceFrames() {
        val counter = BarkEventCounter()
        for (time in 0L..5_000L step 50L) assertEquals(1, counter.update(true, time))
    }

    @Test fun threeSeparateBurstsCountThreeEvenWhileTheModelKeepsTheSameClass() {
        val counter = BarkEventCounter()
        assertEquals(1, counter.update(true, 0))
        counter.update(true, 100)
        counter.update(false, 200)
        counter.update(false, 250)
        assertEquals(2, counter.update(true, 300))
        counter.update(false, 450)
        counter.update(false, 500)
        assertEquals(3, counter.update(true, 550))
    }

    @Test fun briefDropoutDoesNotSplitOneBark() {
        val counter = BarkEventCounter()
        counter.update(true, 0)
        counter.update(false, 100)
        assertEquals(1, counter.update(true, 125))
    }

    @Test fun aBurstInsideTheCooldownDoesNotGetCountedLate() {
        val counter = BarkEventCounter()
        counter.update(true, 0)
        counter.update(false, 50)
        counter.update(false, 100)
        assertEquals(1, counter.update(true, 150))
        assertEquals(1, counter.update(true, 250))
        counter.update(false, 300)
        counter.update(false, 350)
        assertEquals(2, counter.update(true, 400))
    }

    @Test fun stoppingDoesNotResetTheDisplayedTotal() {
        val counter = BarkEventCounter()
        counter.update(true, 0)
        counter.endBurst()
        assertEquals(1, counter.count)
        assertEquals(2, counter.update(true, 2_000))
    }

    @Test fun waveformOnlyUpdatesDoNotPretendToBeNewModelInferences() {
        val policy = BarkDetectionPolicy(0)
        val score = SoundPrediction(listOf(SoundScore(0, "target", 0.95f)), 0.1f)
        assertFalse(policy.evaluate(score).isBark)
        repeat(5) { assertFalse(policy.evaluate(SoundPrediction(emptyList(), 0.1f, false)).isBark) }
        assertTrue(policy.evaluate(score).isBark)
        assertFalse(policy.evaluate(SoundPrediction(emptyList(), 0f, false)).isBark)
        assertTrue(policy.evaluate(SoundPrediction(emptyList(), 0.1f, false)).isBark)
    }
}
