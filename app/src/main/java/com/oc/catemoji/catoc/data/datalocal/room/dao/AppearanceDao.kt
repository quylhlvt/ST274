package com.oc.catemoji.catoc.data.datalocal.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppearanceDao {
    @Query("SELECT * FROM appearance_state")
    fun observeStates(): Flow<List<AppearanceStateEntity>>

    @Query("SELECT * FROM appearance_state WHERE type = :type")
    suspend fun getStates(type: String): List<AppearanceStateEntity>

    @Query("SELECT * FROM appearance_state WHERE type = :type AND assetId = :assetId")
    suspend fun getState(type: String, assetId: String): AppearanceStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(state: AppearanceStateEntity)

    @Query("UPDATE appearance_state SET unlock = 1 WHERE type = :type AND assetId = :assetId")
    suspend fun unlock(type: String, assetId: String)

    @Query("UPDATE appearance_state SET selected = 0 WHERE type = :type AND selected = 1")
    suspend fun clearSelection(type: String)

    @Query("UPDATE appearance_state SET selected = 1 WHERE type = :type AND assetId = :assetId AND unlock = 1")
    suspend fun selectUnlocked(type: String, assetId: String): Int
}
