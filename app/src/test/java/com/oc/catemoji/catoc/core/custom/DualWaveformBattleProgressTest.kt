package com.oc.catemoji.catoc.core.custom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualWaveformBattleProgressTest {
    @Test
    fun recreationRestoresProgressWithoutResettingOrReplayingPoints() {
        val progress = DualWaveformBattleProgress()
        progress.restore(0.72f)
        assertEquals(0.74f, progress.addPlayerPoint(), 0.0001f)
        progress.restore(0f)
        assertTrue(progress.isFinished)
        assertEquals(0f, progress.addPlayerPoint(), 0.0001f)
    }

    @Test
    fun startsInCenterAndEachSideMovesExactlyTwoPercent() {
        val progress = DualWaveformBattleProgress()

        assertEquals(0.50f, progress.value, 0.0001f)
        assertEquals(0.52f, progress.addPlayerPoint(), 0.0001f)
        assertEquals(0.50f, progress.addOpponentPoint(), 0.0001f)
    }

    @Test
    fun playerAndOpponentStopChangingProgressAtEitherEnd() {
        val playerWin = DualWaveformBattleProgress()
        repeat(25) { playerWin.addPlayerPoint() }
        assertEquals(1f, playerWin.value, 0.0001f)
        assertTrue(playerWin.isFinished)
        assertEquals(1f, playerWin.addOpponentPoint(), 0.0001f)

        val opponentWin = DualWaveformBattleProgress()
        repeat(25) { opponentWin.addOpponentPoint() }
        assertEquals(0f, opponentWin.value, 0.0001f)
        assertTrue(opponentWin.isFinished)
        assertEquals(0f, opponentWin.addPlayerPoint(), 0.0001f)
    }

    @Test
    fun resetReturnsFinishedBattleToTheCenter() {
        val progress = DualWaveformBattleProgress()
        repeat(25) { progress.addPlayerPoint() }

        assertEquals(0.50f, progress.reset(), 0.0001f)
        assertEquals(0.48f, progress.addOpponentPoint(), 0.0001f)
    }
}
