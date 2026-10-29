package com.oc.catemoji.catoc.data.repository

import androidx.room.withTransaction
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.entity.AchievementEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.model.statistics.MatchStatistics
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(private val database: AppDatabase) {
    val achievements = database.profileDao().observeAchievements()
    val history = database.historyDao().observeHistory()
    val statistics = history.map { items ->
        MatchStatistics.fromResults(items.map { it.typePlay })
    }

    /** Populate empty lists for the profile demo without replacing saved player data. */
    suspend fun seedDemoItems() = database.withTransaction {
        val dao = database.profileDao()
        if (dao.achievementCount() == 0) {
            val titles = listOf("First Bark", "Battle Pup", "Top Dog", "Bark King",
                "Invincible Hound", "Speed Barker", "Call the Cops!")
            dao.insertAchievements(titles.mapIndexed { index, title ->
                AchievementEntity(index + 1, title,
                    "file:///android_asset/Achievement/${index + 1}. $title.webp")
            })
        }
        if (database.historyDao().count() == 0) {
            val now = System.currentTimeMillis()
            listOf("Buddy", "Max", "Luna", "Charlie", "Rocky").forEachIndexed { index, name ->
                database.historyDao().insert(HistoryEntity(
                    nameBatter = name,
                    date = now - index * 86_400_000L,
                    imageAvatar1 = "file:///android_asset/1/avatar.webp",
                    imageAvatar2 = "file:///android_asset/${index + 2}/avatar.webp",
                    typePlay = index != 2,
                ))
            }
        }
    }
}
