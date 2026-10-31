package com.oc.catemoji.catoc.data.model

import org.junit.Assert.*
import org.junit.Test

class BattleRulesTest {
    @Test fun ranksAdvanceAtBoundariesAndNeverDemoteOrExceedEight() {
        assertEquals(1, BattleRules.rank(1, 99))
        assertEquals(2, BattleRules.rank(1, 100))
        assertEquals(4, BattleRules.rank(4, 0))
        assertEquals(8, BattleRules.rank(7, Int.MAX_VALUE))
    }

    @Test fun badgesRequireTheirActualMatchConditions() {
        assertTrue(BattleRules.earnedBadges(0, 0, 0, 5_000).isEmpty())
        assertEquals(listOf(1), BattleRules.earnedBadges(1, 1, 25, 10_001).map { it.id })
        assertEquals(listOf(1, 2, 5, 6), BattleRules.earnedBadges(5, 5, 25, 10_000).map { it.id })
        assertEquals((1..7).toList(), BattleRules.earnedBadges(25, 5, 100, 5_000).map { it.id })
    }
}
