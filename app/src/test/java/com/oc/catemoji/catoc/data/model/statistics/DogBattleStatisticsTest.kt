package com.oc.catemoji.catoc.data.model.statistics

import org.junit.Assert.*
import org.junit.Test

class DogBattleStatisticsTest {
    @Test
    fun unplayedSkinHasZeroProgressAndLossDoesNotCountAsWin() {
        val initial = DogBattleStatistics()
        assertEquals(0, initial.progressTo(10))
        assertEquals(DogBattleStatistics(1, 0), initial.record(false))
        assertEquals(0, initial.record(false).progressTo(10))
    }

    @Test
    fun onlyWinsAdvanceProgressAndLossesNeverReduceIt() {
        val won = DogBattleStatistics().record(true)
        assertEquals(10, won.progressTo(10))
        val lost = won.record(false)
        assertEquals(DogBattleStatistics(2, 1), lost)
        assertEquals(10, lost.progressTo(10))
        assertEquals(20, lost.record(true).progressTo(10))
        assertEquals(10, lost.record(false).progressTo(10))
    }

    @Test
    fun eachTargetFillsOnlyWhenReachedAndStaysFullAfterwards() {
        (3..32).forEach { target ->
            val before = DogBattleStatistics(100, target - 1L)
            assertTrue(before.progressTo(target) < 100)
            val max = before.record(true)
            assertEquals(100, max.progressTo(target))
            assertEquals(100, max.record(false).progressTo(target))
            assertEquals(100, max.record(true).progressTo(target))
        }
        assertEquals(99, DogBattleStatistics(200, 199).progressTo(200))
        assertEquals(0, DogBattleStatistics(0, -1).progressTo(10))
        assertEquals(100, DogBattleStatistics(Long.MAX_VALUE, Long.MAX_VALUE).progressTo(30))
    }
}
