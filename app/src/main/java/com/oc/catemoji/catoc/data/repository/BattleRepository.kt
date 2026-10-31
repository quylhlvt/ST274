package com.oc.catemoji.catoc.data.repository

import androidx.room.withTransaction
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.dao.DogDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.HistoryDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.UserDao
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.AVATAR
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.FRAME
import javax.inject.Inject
import javax.inject.Singleton
import com.oc.catemoji.catoc.data.datalocal.room.entity.BattleResultEntity
import com.oc.catemoji.catoc.data.model.BattleRules
import kotlinx.coroutines.flow.first

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

    suspend fun completeBattle(
        matchId: String, won: Boolean, opponentName: String, opponentAvatar: String,
        barks: Int, durationMs: Long,
    ): BattleResultEntity = database.withTransaction {
        database.battleResultDao().get(matchId)?.let { return@withTransaction it }
        val user = checkNotNull(userDao.getUser()) { "User profile does not exist" }
        val bones = minOf(BattleRules.bones(won), Int.MAX_VALUE - user.coinbone)
        val points = minOf(BattleRules.rankPoints(won), Int.MAX_VALUE - user.rankbatter)
        val nextRank = BattleRules.rank(user.rank, user.rankbatter + points)
        userDao.upsert(user.copy(coinbone = user.coinbone + bones,
            rankbatter = user.rankbatter + points, rank = nextRank))
        val historyId = historyDao.insert(HistoryEntity(
            nameBatter = opponentName, date = System.currentTimeMillis(),
            imageAvatar1 = user.imageAvatar, imageAvatar2 = opponentAvatar, typePlay = won,
        ))
        val results = historyDao.observeHistory().first()
        val existing = database.profileDao().getAchievements().map { it.id }.toSet()
        val badges = BattleRules.earnedBadges(results.count { it.typePlay },
            results.takeWhile { it.typePlay }.size, barks, durationMs).filter { it.id !in existing }
        if (badges.isNotEmpty()) database.profileDao().insertAchievements(badges)
        BattleResultEntity(matchId, historyId, won, bones, points, user.rank, nextRank,
            badges.joinToString(",") { it.id.toString() }).also { database.battleResultDao().upsert(it) }
    }

    suspend fun claimBattleBonus(matchId: String): BattleResultEntity = database.withTransaction {
        val result = checkNotNull(database.battleResultDao().get(matchId))
        if (result.bonusClaimed) return@withTransaction result
        val user = checkNotNull(userDao.getUser())
        val bonus = minOf(result.bones, Int.MAX_VALUE - user.coinbone)
        userDao.upsert(user.copy(coinbone = user.coinbone + bonus))
        result.copy(bonusClaimed = true).also { database.battleResultDao().upsert(it) }
    }

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
            database.appearanceDao().insertIfMissing(AppearanceStateEntity(AVATAR, "1", selected = true, unlock = true))
            database.appearanceDao().insertIfMissing(AppearanceStateEntity(FRAME, "1", selected = true, unlock = true))
            UserEntity(
                name = trimmedName,
                imageAvatar = "file:///android_asset/1/avatar.webp",
                imageFrame = "file:///android_asset/1/frame.webp",
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
    suspend fun addBattleRank(amount: Int) {
        require(amount > 0)
        check(userDao.addBattleRank(amount) == 1) { "User missing or coin balance limit reached" }
    }
    suspend fun renameUser(name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty())
        check(userDao.updateName(trimmedName) == 1) { "User profile does not exist" }
    }
    suspend fun updateProfile(name: String, avatar: String, frame: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty())
        require(avatar.isNotBlank() && frame.isNotBlank())
        database.withTransaction {
            check(userDao.getUser() != null) { "User profile does not exist" }
            val avatarId = checkNotNull(appearanceAssetId(AVATAR, avatar)) { "Unknown avatar asset" }
            val frameId = checkNotNull(appearanceAssetId(FRAME, frame)) { "Unknown frame asset" }
            val dao = database.appearanceDao()
            check(dao.getState(AVATAR, avatarId)?.unlock == true) { "Avatar is locked" }
            check(dao.getState(FRAME, frameId)?.unlock == true) { "Frame is locked" }
            dao.clearSelection(AVATAR)
            dao.clearSelection(FRAME)
            check(dao.selectUnlocked(AVATAR, avatarId) == 1)
            check(dao.selectUnlocked(FRAME, frameId) == 1)
            check(userDao.updateProfile(trimmedName, avatar, frame) == 1) { "User profile does not exist" }
        }
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
