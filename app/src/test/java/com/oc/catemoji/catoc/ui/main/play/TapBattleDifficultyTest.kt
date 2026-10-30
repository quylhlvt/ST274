package com.oc.catemoji.catoc.ui.main.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapBattleDifficultyTest {
    @Test
    fun beginnersGetOneOpponentPushEveryThreeTenthsOfASecond() {
        assertEquals(300L, TapBattleDifficulty.opponentIntervalMs(1))
    }

    @Test
    fun allEightRanksBecomeStrictlyFaster() {
        val intervals = (1..8).map(TapBattleDifficulty::opponentIntervalMs)
        assertTrue(intervals.all { it > 0 })
        assertTrue(intervals.zipWithNext().all { (previous, next) -> next < previous })
        assertEquals(100L, intervals.last())
    }

    @Test
    fun unknownOrOutOfRangeRanksUseTheNearestSupportedLevel() {
        assertEquals(300L, TapBattleDifficulty.opponentIntervalMs(Int.MIN_VALUE))
        assertEquals(300L, TapBattleDifficulty.opponentIntervalMs(0))
        assertEquals(100L, TapBattleDifficulty.opponentIntervalMs(Int.MAX_VALUE))
    }
}
