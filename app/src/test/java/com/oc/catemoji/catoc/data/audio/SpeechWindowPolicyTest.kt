package com.oc.catemoji.catoc.data.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechWindowPolicyTest {
    @Test fun quietBarkFlushesAfterEightyMillisecondGapWithoutCalibration() {
        val policy = SpeechWindowPolicy(16_000)
        assertFalse(policy.accept(0.01f, 640))
        assertFalse(policy.accept(0.01f, 640))
        assertFalse(policy.accept(0.001f, 640))
        assertTrue(policy.accept(0.001f, 640))
        assertFalse(policy.accept(0.001f, 640))
    }

    @Test fun rapidlyRepeatedBarksEachGetAWindowWithoutEightHundredMillisecondWait() {
        val policy = SpeechWindowPolicy(16_000)
        repeat(12) {
            assertFalse(policy.accept(0.025f, 640))
            assertFalse(policy.accept(0.025f, 640))
            assertFalse(policy.accept(0.001f, 640))
            assertTrue(policy.accept(0.001f, 640))
        }
    }

    @Test fun heldSoundIsNotCutEvenAfterManySeconds() {
        val policy = SpeechWindowPolicy(16_000)
        repeat(500) { assertFalse(policy.accept(0.03f, 640)) }
        assertFalse(policy.accept(0.001f, 640))
        assertTrue(policy.accept(0.001f, 640))
    }

    @Test fun shortDipInsideASoundDoesNotCloseItsWindow() {
        val policy = SpeechWindowPolicy(16_000)
        assertFalse(policy.accept(0.03f, 1280))
        assertFalse(policy.accept(0.002f, 640))
        assertFalse(policy.accept(0.025f, 640))
        assertFalse(policy.accept(0.002f, 640))
        assertTrue(policy.accept(0.002f, 640))
    }

    @Test fun usesPeakRelativeGapForLoudBarks() {
        val policy = SpeechWindowPolicy(16_000)
        assertFalse(policy.accept(0.1f, 1280))
        // A 0.01 floor is above the fixed silence threshold but well below this bark.
        assertFalse(policy.accept(0.01f, 640))
        assertTrue(policy.accept(0.01f, 640))
    }

    @Test fun silenceAndSubthresholdBackgroundNeverCreateWindows() {
        val policy = SpeechWindowPolicy(16_000)
        repeat(1000) {
            assertFalse(policy.accept(0f, 640))
            assertFalse(policy.accept(0.005f, 640))
        }
        assertFalse(policy.accept(0f, 1280))
    }

    @Test fun separatedClicksCannotAccumulateEnoughForegroundToArm() {
        val policy = SpeechWindowPolicy(16_000)
        repeat(100) {
            assertFalse(policy.accept(0.04f, 640))
            assertFalse(policy.accept(0f, 1280))
        }
    }

    @Test fun timingDependsOnSampleCountsRatherThanReadBoundaries() {
        for (sampleRate in listOf(8_000, 16_000, 48_000)) {
            val policy = SpeechWindowPolicy(sampleRate)
            val eightyMillis = sampleRate * 80 / 1000
            assertFalse(policy.accept(0.01f, eightyMillis - 1))
            assertFalse(policy.accept(0.01f, 1))
            assertFalse(policy.accept(0.001f, eightyMillis - 1))
            assertTrue(policy.accept(0.001f, 1))
        }
    }

    @Test fun nativeEndpointResetDiscardsThePreviousBurstAndPeak() {
        val policy = SpeechWindowPolicy(16_000)
        assertFalse(policy.accept(0.3f, 1280))
        policy.reset()
        assertFalse(policy.accept(0f, 1280))
        assertFalse(policy.accept(0.01f, 1280))
        assertTrue(policy.accept(0.001f, 1280))
    }

    @Test fun zeroLengthAndInvalidLevelsDoNotAdvanceTheGap() {
        val policy = SpeechWindowPolicy(16_000)
        assertFalse(policy.accept(0.01f, 1280))
        assertFalse(policy.accept(0f, 0))
        assertFalse(policy.accept(Float.NaN, 1280))
        assertFalse(policy.accept(Float.POSITIVE_INFINITY, 1280))
        assertFalse(policy.accept(-1f, 1280))
        assertFalse(policy.accept(0.001f, 640))
        assertTrue(policy.accept(0.001f, 640))
    }

    @Test fun longReadsCannotOverflowForegroundOrQuietCounters() {
        val policy = SpeechWindowPolicy(16_000)
        repeat(3) { assertFalse(policy.accept(0.01f, Int.MAX_VALUE)) }
        assertTrue(policy.accept(0.001f, Int.MAX_VALUE))
        assertFalse(policy.accept(0.001f, Int.MAX_VALUE))
    }

    @Test fun aQuietOrLateVoicedOnsetStillClosesAtItsAcousticGap() {
        val policy = SpeechWindowPolicy(16_000)
        policy.onVoicedOnset()
        // The pre-roll already contains the onset; this frame need not arm again
        // at the older 0.006 level or accumulate a second 80 ms foreground period.
        assertFalse(policy.accept(0.0025f, 640))
        assertFalse(policy.accept(0.0005f, 640))
        assertTrue(policy.accept(0.0005f, 640))
    }
}
