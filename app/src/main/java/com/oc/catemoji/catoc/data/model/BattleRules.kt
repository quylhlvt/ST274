package com.oc.catemoji.catoc.data.model

import com.oc.catemoji.catoc.data.datalocal.room.entity.AchievementEntity

/** Game economy in one place so balancing does not depend on the result views. */
object BattleRules {
    const val MAX_RANK = 8
    const val POINTS_PER_RANK = 100
    fun bones(won: Boolean) = if (won) 100 else 25
    fun rankPoints(won: Boolean) = if (won) 30 else 10
    fun rank(current: Int, points: Int) = maxOf(current, points / POINTS_PER_RANK + 1).coerceIn(1, MAX_RANK)

    private val titles = listOf("First Bark", "Battle Pup", "Top Dog", "Bark King",
        "Invincible Hound", "Speed Barker", "Call the Cops!")
    fun badge(id: Int) = AchievementEntity(id, titles[id - 1], "file:///android_asset/Achievement/$id.webp")

    fun earnedBadges(wins: Int, streak: Int, barks: Int, durationMs: Long): List<AchievementEntity> = buildList {
        if (barks > 0) add(badge(1))
        if (wins >= 5) add(badge(2))
        if (wins >= 10) add(badge(3))
        if (wins >= 25) add(badge(4))
        if (streak >= 5) add(badge(5))
        if (barks >= 25 && durationMs in 1..10_000) add(badge(6))
        if (barks >= 100) add(badge(7))
    }
}
