package com.oc.catemoji.catoc.data.datalocal.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.oc.catemoji.catoc.data.model.BattleRules

/** Durable receipt makes result delivery and the earned-ad callback safe to retry. */
@Entity(tableName = "battle_result")
data class BattleResultEntity(
    @PrimaryKey val matchId: String,
    val historyId: Long,
    val won: Boolean,
    val bones: Int,
    val rankPoints: Int,
    val previousRank: Int,
    val nextRank: Int,
    val badgeIds: String,
    val bonusClaimed: Boolean = false,
) {
    fun badges(): List<AchievementEntity> = badgeIds.split(',').mapNotNull { it.toIntOrNull() }
            .filter { it in 1..7 }.map(BattleRules::badge)
}
