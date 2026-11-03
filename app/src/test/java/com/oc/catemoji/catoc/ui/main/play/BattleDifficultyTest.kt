package com.oc.catemoji.catoc.ui.main.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.oc.catemoji.catoc.data.model.DogSkinCatalogue

class BattleDifficultyTest {
    @Test
    fun firstSkinStartsAtTheBeginnerCadenceForEachMode() {
        assertEquals(300L, BattleDifficulty.opponentIntervalMs(1, 0, false))
        assertEquals(1_500L, BattleDifficulty.opponentIntervalMs(1, 0, true))
    }

    @Test
    fun laterSkinsAndHigherProgressIncreaseTheRequiredCadenceInBothModes() {
        listOf(false, true).forEach { microphone ->
            (0..100).forEach { percent ->
                val intervals = (1..DogSkinCatalogue.SKIN_COUNT).map {
                    BattleDifficulty.opponentIntervalMs(it, percent, microphone)
                }
                assertTrue(intervals.all { it > 0 })
                assertTrue(intervals.zipWithNext().all { (previous, next) -> next < previous })
            }
            (1..DogSkinCatalogue.SKIN_COUNT).forEach { skin ->
                val intervals = (0..100).map { BattleDifficulty.opponentIntervalMs(skin, it, microphone) }
                assertTrue(intervals.zipWithNext().all { (previous, next) -> next <= previous })
                assertTrue(intervals.first() > intervals.last())
            }
        }
        assertEquals(120L, BattleDifficulty.opponentIntervalMs(30, 100, false))
        assertEquals(600L, BattleDifficulty.opponentIntervalMs(30, 100, true))
    }

    @Test
    fun outOfRangeSkinAndProgressUseTheNearestSupportedValue() {
        listOf(false, true).forEach { microphone ->
            assertEquals(BattleDifficulty.opponentIntervalMs(1, 0, microphone),
                BattleDifficulty.opponentIntervalMs(Int.MIN_VALUE, Int.MIN_VALUE, microphone))
            assertEquals(BattleDifficulty.opponentIntervalMs(30, 100, microphone),
                BattleDifficulty.opponentIntervalMs(Int.MAX_VALUE, Int.MAX_VALUE, microphone))
        }
    }
}
