package com.oc.catemoji.catoc.data.repository

import androidx.room.withTransaction
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.dao.DogDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.HistoryDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.UserDao
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BattleRepository @Inject constructor(
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val dogDao: DogDao,
    private val historyDao: HistoryDao,
) {
    // The shared app profile uses UserEntity's default primary key (1).
    val user = userDao.observeUser()
    val dogs = dogDao.observeDogs()
    val history = historyDao.observeHistory()

    suspend fun createProfile(name: String): UserEntity {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty())
        return database.withTransaction {
            val existing = userDao.getUser()
            if (existing != null) {
                return@withTransaction existing.copy(name = trimmedName).also { userDao.upsert(it) }
            }
            val dogId = dogDao.insert(
                DogEntity(stepRank = 1, progress = 0, unlock = true, selected = true),
            ).toInt()
            UserEntity(
                name = trimmedName,
                imageAvatar = "file:///android_asset/1/avatar.png",
                imageFrame = "file:///android_asset/1/frame.png",
                coinbone = 100,
                rank = 1,
                rankbatter = 0,
                iddog = dogId,
            ).also { userDao.upsert(it) }
            // No history row is created before the first match.
        }
    }

    suspend fun saveUser(user: UserEntity) = userDao.upsert(user)
    suspend fun addBones(amount: Int) {
        require(amount > 0)
        check(userDao.addBones(amount) == 1) { "User missing or coin balance limit reached" }
    }
    suspend fun renameUser(name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty())
        check(userDao.updateName(trimmedName) == 1) { "User profile does not exist" }
    }
    suspend fun deleteUser(user: UserEntity) = userDao.delete(user)
    suspend fun saveDog(dog: DogEntity) = dogDao.upsert(dog)
    suspend fun saveDogs(dogs: List<DogEntity>) = dogDao.upsertAll(dogs)
    suspend fun deleteDog(dog: DogEntity) = dogDao.delete(dog)
    suspend fun addHistory(history: HistoryEntity): Long = historyDao.insert(history)
    suspend fun updateHistory(history: HistoryEntity) = historyDao.update(history)
    suspend fun deleteHistory(history: HistoryEntity) = historyDao.delete(history)
    suspend fun clearHistory() = historyDao.clear()
}
