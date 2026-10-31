package com.oc.catemoji.catoc.data.datalocal.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.oc.catemoji.catoc.data.datalocal.room.entity.BattleResultEntity

@Dao
interface BattleResultDao {
    @Query("SELECT * FROM battle_result WHERE matchId = :matchId")
    suspend fun get(matchId: String): BattleResultEntity?

    @Upsert
    suspend fun upsert(result: BattleResultEntity)
}
