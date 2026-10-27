package com.oc.catemoji.catoc.data.datalocal.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.repository.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun demoListsArePersistedWithoutDuplicatesOnConcurrentOrRepeatedLoads() = runBlocking {
        val repository = ProfileRepository(database)
        coroutineScope {
            List(3) { async { repository.seedDemoItems() } }.awaitAll()
        }
        val history = repository.history.first()
        assertEquals(5, history.size)
        assertEquals(5, repository.statistics.first().total)
        assertEquals(4, repository.statistics.first().wins)
        assertEquals(1, repository.statistics.first().losses)
        assertEquals(80.0, repository.statistics.first().winRate, 0.001)
        assertEquals(7, repository.achievements.first().size)
        assertTrue(history.zipWithNext().all { (a, b) -> a.date > b.date })
        repository.seedDemoItems()
        assertEquals(history, repository.history.first())
        repository.achievements.first().forEach { achievement ->
            context.assets.open(achievement.image.removePrefix("file:///android_asset/")).use {
                assertTrue(it.read() >= 0)
            }
        }
    }

    @Test
    fun seedingPreservesExistingHistoryAndProfile() = runBlocking {
        val user = UserEntity(name = "Player", coinbone = 42, rank = 2)
        database.userDao().upsert(user)
        val saved = HistoryEntity(nameBatter = "Saved match", date = 1000,
            imageAvatar1 = "a", imageAvatar2 = "b", typePlay = false)
        val id = database.historyDao().insert(saved)
        val repository = ProfileRepository(database)
        repository.seedDemoItems()
        assertEquals(listOf(saved.copy(id = id)), repository.history.first())
        assertEquals(user, database.userDao().getUser())
        assertEquals(1, repository.statistics.first().total)
        assertEquals(0, repository.statistics.first().wins)
        assertEquals(1, repository.statistics.first().losses)
        assertEquals(7, repository.achievements.first().size)
    }

    @Test
    fun statisticsChangeWhenHistoryIsAddedUpdatedOrCleared() = runBlocking {
        val repository = ProfileRepository(database)
        val match = HistoryEntity(nameBatter = "Match", date = 1000,
            imageAvatar1 = "a", imageAvatar2 = "b", typePlay = true)
        assertEquals(0, repository.statistics.first().total)
        val id = database.historyDao().insert(match)
        assertEquals(100.0, repository.statistics.first().winRate, 0.001)
        database.historyDao().update(match.copy(id = id, typePlay = false))
        assertEquals(0, repository.statistics.first().wins)
        assertEquals(1, repository.statistics.first().losses)
        database.historyDao().clear()
        assertEquals(0, repository.statistics.first().total)
        assertEquals(0.0, repository.statistics.first().winRate, 0.001)
    }

    @Test
    fun migrationConvertsLegacyResultsAndPreservesHistory() = runBlocking {
        val filename = "profile-migration-${UUID.randomUUID()}.db"
        try {
            val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(
                "com.oc.catemoji.catoc.data.datalocal.room.AppDatabase/3.json",
            ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            context.openOrCreateDatabase(filename, 0, null).use { legacy ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    legacy.execSQL(entity.getString("createSql").replace(
                        "\${TABLE_NAME}", entity.getString("tableName"),
                    ))
                }
                listOf("victory", "defeat", "true", "false", "1", "0").forEachIndexed { index, result ->
                    legacy.execSQL("INSERT INTO history VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(index + 1, "Saved $index", 1000L + index, "a", "b", result))
                }
                legacy.version = 3
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, filename)
                .addMigrations(AppDatabase.MIGRATION_3_4).build()
            try {
                val results = migrated.historyDao().observeHistory().first().sortedBy { it.id }
                assertEquals(listOf(true, false, true, false, true, false), results.map { it.typePlay })
                assertEquals((1L..6L).toList(), results.map { it.id })
                assertEquals("Saved 0", results.first().nameBatter)
                assertEquals(1000L, results.first().date)
                val nextId = migrated.historyDao().insert(results.last().copy(id = 0))
                assertTrue(nextId > 6)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
    }
}
