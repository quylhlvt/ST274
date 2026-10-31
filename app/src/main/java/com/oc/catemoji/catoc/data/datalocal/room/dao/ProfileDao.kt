package com.oc.catemoji.catoc.data.datalocal.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.oc.catemoji.catoc.data.datalocal.room.entity.AchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM achievement ORDER BY id")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievement ORDER BY id")
    suspend fun getAchievements(): List<AchievementEntity>

    @Query("SELECT COUNT(*) FROM achievement")
    suspend fun achievementCount(): Int

    @Insert
    suspend fun insertAchievements(items: List<AchievementEntity>)
}
