package com.oc.catemoji.catoc.data.audio

import org.junit.Assert.*
import org.junit.Test

class BarkDetectionPolicyTest {
    private fun prediction(index: Int, score: Float = 0.95f, level: Float = 0.1f) =
        SoundPrediction(listOf(SoundScore(index, "class $index", score)), level)

    @Test fun silenceNeverCountsEvenWhenItsModelClassIsTheConfiguredTarget() {
        val policy = BarkDetectionPolicy(0)
        repeat(5) { assertFalse(policy.evaluate(prediction(0, level = 0f)).isBark) }
    }

    @Test fun briefAndLowConfidenceMatchesDoNotTriggerAndUnrelatedSoundsResetTheStreak() {
        val policy = BarkDetectionPolicy(1)
        assertFalse(policy.evaluate(prediction(1)).isBark)
        assertFalse(policy.evaluate(prediction(0)).isBark)
        assertFalse(policy.evaluate(prediction(1)).isBark)
        assertFalse(policy.evaluate(prediction(1, score = 0.6f)).isBark)
        assertFalse(policy.evaluate(prediction(1)).isBark)
        assertTrue(policy.evaluate(prediction(1)).isBark)
        assertFalse(policy.evaluate(prediction(2)).isBark)
    }

    @Test fun labelsAreNotGuessedAndOnlyTheConfiguredClassCanTrigger() {
        val policy = BarkDetectionPolicy(1)
        repeat(4) { assertFalse(policy.evaluate(prediction(0)).isBark) }
        assertFalse(policy.evaluate(prediction(1)).isBark)
        assertTrue(policy.evaluate(prediction(1)).isBark)
    }

    @Test fun aLowerScoringTargetDoesNotOverrideTheDominantOtherSound() {
        val policy = BarkDetectionPolicy(0)
        val mixed = SoundPrediction(listOf(SoundScore(0, "target", 0.8f), SoundScore(1, "other", 0.95f)), 0.1f)
        repeat(3) { assertFalse(policy.evaluate(mixed).isBark) }
    }

    @Test fun invalidScoresAndVolumesCannotTriggerRecognition() {
        val policy = BarkDetectionPolicy(0)
        assertFalse(policy.evaluate(prediction(0)).isBark)
        assertFalse(policy.evaluate(prediction(0, score = Float.NaN)).isBark)
        assertFalse(policy.evaluate(prediction(0, score = 2f)).isBark)
        assertFalse(policy.evaluate(prediction(0, level = Float.NaN)).isBark)
        assertFalse(policy.evaluate(SoundPrediction(emptyList(), 0f)).isBark)
    }
}
