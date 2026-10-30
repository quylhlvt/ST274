package com.oc.catemoji.catoc.ui.main.play

/** Opponent cadence for the eight tap-mode ranks. Each tick pushes one point. */
object TapBattleDifficulty {
    private val intervalsMs = longArrayOf(300, 270, 240, 210, 180, 150, 120, 100)

    fun opponentIntervalMs(rank: Int): Long =
        intervalsMs[rank.coerceIn(1, intervalsMs.size) - 1]
}
