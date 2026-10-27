package com.oc.catemoji.catoc.data.datalocal.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user WHERE id = :id")
    fun observeUser(id: Int = 1): Flow<UserEntity?>

    @Query("SELECT * FROM user WHERE id = :id")
    suspend fun getUser(id: Int = 1): UserEntity?

    @Query("UPDATE user SET name = :name WHERE id = :id")
    suspend fun updateName(name: String, id: Int = 1): Int

    @Query("""
        UPDATE user SET coinbone = coinbone + :amount
        WHERE id = :id AND coinbone <= 2147483647 - :amount
    """)
    suspend fun addBones(amount: Int, id: Int = 1): Int
 @Query("""
        UPDATE user SET rankbatter = rankbatter + :amount
        WHERE id = :id AND rankbatter <= 2147483647 - :amount
    """)
    suspend fun addBattleRank(amount: Int, id: Int = 1): Int

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Delete
    suspend fun delete(user: UserEntity)
}
