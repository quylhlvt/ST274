package com.oc.catemoji.catoc.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BarkBurstScoreTrackerTest {
    @Test
    fun predictionArrivingBeforeTheQuietGapIsNotLost() {
        val tracker = BarkBurstScoreTracker()
        tracker.offerPcm(signal {
            add(800, 0.002f)
            add(200, 0.08f)
        })
        // The model has already heard the whole bark, but the two quiet frames
        // required to close the PCM burst have not arrived yet.
        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 1_000L).newPoints)
        tracker.offerPcm(signal { add(40, 0.002f) })
        assertEquals(1, tracker.offerAiPrediction(0.95f, 200L, 1_200L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.2f, 400L, 1_400L).newPoints)
        assertEquals(1, tracker.currentPoints())
    }

    @Test
    fun cachedConfidenceSpikeStillNeedsAnotherValidConfirmation() {
        val tracker = BarkBurstScoreTracker()
        tracker.offerPcm(signal {
            add(800, 0.002f)
            add(200, 0.08f)
        })
        tracker.offerAiPrediction(0.95f, 0L, 1_000L)
        tracker.offerPcm(signal { add(40, 0.002f) })
        assertEquals(0, tracker.offerAiPrediction(0.2f, 200L, 1_200L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.95f, 400L, 1_400L).newPoints)
        assertEquals(0, tracker.currentPoints())
    }

    @Test
    fun confirmationOnPcmArrivalCannotBeReplayedByAnotherPrediction() {
        val tracker = BarkBurstScoreTracker()
        tracker.offerPcm(signal {
            add(200, 0.002f)
            add(200, 0.08f)
            add(20, 0.002f)
        })
        tracker.offerAiPrediction(0.95f, 0L, 420L)
        tracker.offerAiPrediction(0.95f, 0L, 440L)
        tracker.offerPcm(signal { add(40, 0.002f) })
        assertEquals(1, tracker.currentPoints())
        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 600L).newPoints)
        assertEquals(1, tracker.currentPoints())
    }

    @Test
    fun repeatedBarksSeparatedByAudibleValleysEachReceiveAPoint() {
        val tracker = BarkBurstScoreTracker()
        val pcm = signal {
            add(200, 0.002f)
            add(160, 0.08f)
            add(40, 0.015f) // Echo remains above the old absolute release level.
            add(140, 0.075f)
            add(40, 0.014f)
            add(180, 0.085f)
            add(80, 0.002f)
        }

        tracker.offerPcm(pcm)

        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 1_000L).newPoints)
        val update = tracker.offerAiPrediction(0.95f, 100L, 1_100L)
        assertEquals(3, update.newPoints)
        assertEquals(3, update.totalPoints)
        assertEquals(0, tracker.offerAiPrediction(0.95f, 200L, 1_200L).newPoints)
    }

    @Test
    fun continuousNoiseNeverManufacturesPointsEvenWithHighModelConfidence() {
        val tracker = BarkBurstScoreTracker()
        tracker.offerPcm(signal {
            add(200, 0.002f)
            add(1_400, 0.08f)
            add(80, 0.002f)
        })

        assertEquals(0, tracker.offerAiPrediction(0.99f, 0L, 1_000L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.99f, 600L, 1_680L).newPoints)
        assertEquals(0, tracker.currentPoints())
    }

    @Test
    fun lowConfidenceSoundDoesNotScoreAndCanBeConfirmedByALaterWindow() {
        val tracker = BarkBurstScoreTracker()
        tracker.offerPcm(signal {
            add(200, 0.002f)
            add(180, 0.08f)
            add(80, 0.002f)
        })

        assertEquals(0, tracker.offerAiPrediction(0.84f, 0L, 600L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 800L).newPoints)
        assertEquals(1, tracker.offerAiPrediction(0.95f, 100L, 1_000L).newPoints)
        assertEquals(1, tracker.currentPoints())
    }

    @Test
    fun silenceAndQuietBackgroundBurstsNeverScoreEvenIfModelReportsBark() {
        listOf(0f, 0.002f, 0.02f).forEach { amplitude ->
            val tracker = BarkBurstScoreTracker()
            tracker.offerPcm(signal {
                add(200, 0.002f)
                add(180, amplitude)
                add(80, 0.002f)
            })
            assertEquals(0, tracker.offerAiPrediction(0.99f, 0L, 800L).newPoints)
            assertEquals(0, tracker.offerAiPrediction(0.99f, 100L, 1_000L).totalPoints)
        }
    }

    @Test
    fun isolatedConfidenceSpikeDoesNotConfirmABurst() {
        val tracker = singleBurstTracker()
        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 600L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.2f, 0L, 800L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 1_000L).newPoints)
        assertEquals(0, tracker.currentPoints())
    }

    @Test
    fun duplicateAndOutOfOrderPredictionsCannotConfirmOrReplayPoints() {
        val tracker = singleBurstTracker()
        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 800L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 800L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.95f, 0L, 600L).newPoints)
        assertEquals(1, tracker.offerAiPrediction(0.95f, 100L, 1_000L).newPoints)
        assertEquals(0, tracker.offerAiPrediction(0.95f, 200L, 1_200L).newPoints)
        assertEquals(1, tracker.currentPoints())
    }

    @Test
    fun invalidScoresNeverConfirmAndResetClearsPendingBursts() {
        val tracker = singleBurstTracker()
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 2f).forEachIndexed { index, score ->
            assertEquals(0, tracker.offerAiPrediction(score, 0L, 600L + index * 100L).newPoints)
        }
        tracker.reset()
        tracker.offerAiPrediction(0.99f, 0L, 800L)
        assertEquals(0, tracker.offerAiPrediction(0.99f, 100L, 1_000L).totalPoints)
    }

    private fun singleBurstTracker() = BarkBurstScoreTracker().apply {
        offerPcm(signal {
            add(200, 0.002f)
            add(180, 0.08f)
            add(80, 0.002f)
        })
    }

    private fun signal(block: SignalBuilder.() -> Unit): ShortArray =
        SignalBuilder().apply(block).build()

    private class SignalBuilder {
        private val samples = ArrayList<Short>()

        fun add(durationMs: Int, amplitude: Float) {
            require(durationMs % 20 == 0 && amplitude in 0f..1f)
            val value = (amplitude * Short.MAX_VALUE).toInt().toShort()
            repeat(SAMPLE_RATE * durationMs / 1_000) { samples.add(value) }
        }

        fun build(): ShortArray = ShortArray(samples.size) { samples[it] }

        private companion object {
            const val SAMPLE_RATE = 44_100
        }
    }
}
