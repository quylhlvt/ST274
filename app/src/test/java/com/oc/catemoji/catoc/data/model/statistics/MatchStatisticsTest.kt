package com.oc.catemoji.catoc.data.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchStatisticsTest {
    @Test
    fun noMatchesHasZeroCountsAndRate() {
        assertEquals(MatchStatistics(0, 0, 0, 0.0, 0), MatchStatistics.fromResults(emptyList()))
    }

    @Test
    fun mixedResultsComputeWinRateWithoutIntegerDivisionAndCurrentStreak() {
        val summary = MatchStatistics.fromResults(listOf(true, true, false, true, false, false))
        assertEquals(MatchStatistics(6, 3, 3, 50.0, 2), summary)
        assertEquals(100.0 / 3, MatchStatistics.fromResults(listOf(false, true, false)).winRate, 0.00001)
    }

    @Test
    fun allWinsHaveFullRateAndStreak() {
        assertEquals(MatchStatistics(3, 3, 0, 100.0, 3),
            MatchStatistics.fromResults(listOf(true, true, true)))
    }

    @Test
    fun newestLossResetsStreakAndAllLossesHaveZeroRate() {
        assertEquals(0, MatchStatistics.fromResults(listOf(false, true, true)).streak)
        assertEquals(MatchStatistics(2, 0, 2, 0.0, 0),
            MatchStatistics.fromResults(listOf(false, false)))
    }
}
