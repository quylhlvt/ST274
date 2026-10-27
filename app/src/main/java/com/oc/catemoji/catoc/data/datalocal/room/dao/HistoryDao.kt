package com.oc.catemoji.catoc.data.datalocal.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY date DESC, id DESC")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getHistory(id: Long): HistoryEntity?

    @Insert
    suspend fun insert(history: HistoryEntity): Long

    @Update
    suspend fun update(history: HistoryEntity)

    @Delete
    suspend fun delete(history: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}
