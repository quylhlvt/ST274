package com.oc.catemoji.catoc.data.datalocal.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DogDao {
    @Insert
    suspend fun insert(dog: DogEntity): Long

    @Query("SELECT * FROM dog ORDER BY iddog")
    fun observeDogs(): Flow<List<DogEntity>>

    @Query("SELECT * FROM dog WHERE iddog = :iddog")
    suspend fun getDog(iddog: Int): DogEntity?

    @Upsert
    suspend fun upsert(dog: DogEntity)

    @Upsert
    suspend fun upsertAll(dogs: List<DogEntity>)

    @Delete
    suspend fun delete(dog: DogEntity)
}
