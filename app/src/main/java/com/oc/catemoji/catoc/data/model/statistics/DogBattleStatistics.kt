package com.oc.catemoji.catoc.data.model.statistics

data class DogBattleStatistics(val played: Long = 0, val wins: Long = 0) {
    fun progressTo(winTarget: Int): Int {
        require(winTarget > 0)
        return (wins.coerceIn(0, winTarget.toLong()) * 100 / winTarget).toInt()
    }

    fun record(won: Boolean): DogBattleStatistics = copy(
        played = played + 1,
        wins = wins + if (won) 1 else 0,
    )
}
