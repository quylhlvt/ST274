package com.oc.catemoji.catoc.data.model.statistics

data class MatchStatistics(
    val total: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val streak: Int,
) {
    companion object {
        /** Results must be ordered newest first, as returned by HistoryDao. */
        fun fromResults(results: List<Boolean>): MatchStatistics {
            val wins = results.count { it }
            return MatchStatistics(
                total = results.size,
                wins = wins,
                losses = results.size - wins,
                winRate = if (results.isEmpty()) 0.0 else wins.toDouble() / results.size * 100,
                streak = results.takeWhile { it }.size,
            )
        }
    }
}
