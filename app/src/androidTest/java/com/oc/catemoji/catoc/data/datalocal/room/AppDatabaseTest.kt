package com.oc.catemoji.catoc.data.datalocal.room

import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.ui.main.home.HomeEvent
import com.oc.catemoji.catoc.ui.main.home.HomeViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository() = BattleRepository(
        database, database.userDao(), database.dogDao(), database.historyDao(),
    )

    @Test
    fun homeObservesRoomAndRenamePreservesCoinsAndRank() = runBlocking {
        val repository = repository()
        val user = repository.createProfile("Player")
        repository.saveUser(user.copy(coinbone = 250, rank = 3, rankbatter = 80))
        val viewModel = HomeViewModel(repository, context)
        val store = ViewModelStore().apply { put("home", viewModel) }
        try {
            val initial = withTimeout(5_000) { viewModel.uiState.first { !it.loading } }
            assertEquals(250, initial.user?.coinbone)
            assertEquals(3, initial.user?.rank)
            assertEquals(80, initial.user?.rankbatter)
            assertEquals(user.iddog, initial.dog?.iddog)
            assertEquals("file:///android_asset/1/state/puppy/1.png", initial.dogPreview)

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.renameUser("   Renamed   ")
            }
            assertEquals(HomeEvent.NAME_SAVED, withTimeout(5_000) { viewModel.events.first() })
            val updated = withTimeout(5_000) { viewModel.uiState.first { it.user?.name == "Renamed" } }
            assertEquals(250, updated.user?.coinbone)
            assertEquals(3, updated.user?.rank)
            assertEquals(80, updated.user?.rankbatter)

            InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.add100Bones() }
            val rewarded = withTimeout(5_000) { viewModel.uiState.first { it.user?.coinbone == 350 } }
            assertEquals(350, database.userDao().getUser()?.coinbone)
            assertEquals("Renamed", rewarded.user?.name)
            assertEquals(3, rewarded.user?.rank)
            assertEquals(80, rewarded.user?.rankbatter)

            InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.renameUser("  ") }
            assertEquals(HomeEvent.NAME_REQUIRED, withTimeout(5_000) { viewModel.events.first() })
            assertEquals("Renamed", database.userDao().getUser()?.name)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        }
    }

    @Test
    fun simultaneousBoneAdditionsDoNotLoseUpdates() = runBlocking {
        val repository = repository()
        repository.createProfile("Player")
        coroutineScope {
            List(10) { async { repository.addBones(100) } }.awaitAll()
        }
        assertEquals(1100, database.userDao().getUser()?.coinbone)
        assertEquals("Player", database.userDao().getUser()?.name)
    }

    @Test
    fun renamingMissingUserDoesNotCreateOrResetProfile() = runBlocking {
        var failed = false
        try {
            repository().renameUser("Name")
        } catch (error: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertNull(database.userDao().getUser())
        assertTrue(database.dogDao().observeDogs().first().isEmpty())
    }

    @Test
    fun createProfileUsesRequestedDefaultsAndDoesNotResetExistingProgress() = runBlocking {
        val repository = repository()
        val user = repository.createProfile("  Player  ")
        assertTrue(user.iddog > 0)
        assertEquals("Player", user.name)
        assertEquals(100, user.coinbone)
        assertEquals(1, user.rank)
        assertEquals(0, user.rankbatter)
        assertEquals("file:///android_asset/1/avatar.png", user.imageAvatar)
        assertEquals("file:///android_asset/1/frame.png", user.imageFrame)
        context.assets.open("1/avatar.png").use { assertTrue(it.read() >= 0) }
        context.assets.open("1/frame.png").use { assertTrue(it.read() >= 0) }
        assertEquals(
            DogEntity(user.iddog, stepRank = 1, progress = 0, unlock = true, selected = true),
            database.dogDao().getDog(user.iddog),
        )
        assertTrue(database.historyDao().observeHistory().first().isEmpty())

        val progressed = user.copy(coinbone = 25, rank = 3, rankbatter = 2)
        database.userDao().upsert(progressed)
        repository.createProfile("Renamed")
        assertEquals(progressed.copy(name = "Renamed"), database.userDao().getUser())
        assertEquals(1, database.dogDao().observeDogs().first().size)
    }

    @Test
    fun simultaneousProfileSavesCreateOnlyOneDog() = runBlocking {
        val repository = repository()
        coroutineScope {
            listOf(
                async { repository.createProfile("First") },
                async { repository.createProfile("Second") },
            ).awaitAll()
        }
        val dog = database.dogDao().observeDogs().first().single()
        assertEquals(dog.iddog, database.userDao().getUser()?.iddog)
    }

    @Test
    fun failedUserSaveRollsBackNewDog() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_user BEFORE INSERT ON user BEGIN SELECT RAISE(ABORT, 'test failure'); END",
        )
        var failed = false
        try {
            repository().createProfile("Player")
        } catch (error: SQLiteException) {
            failed = true
        }
        assertTrue(failed)
        assertNull(database.userDao().getUser())
        assertTrue(database.dogDao().observeDogs().first().isEmpty())
    }

    @Test
    fun migrationPreservesExistingDataAndGeneratesNextDogId() = runBlocking {
        val filename = "room-migration-${UUID.randomUUID()}.db"
        try {
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val schema = testContext.assets.open(
                "com.oc.catemoji.catoc.data.datalocal.room.AppDatabase/1.json",
            ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            context.openOrCreateDatabase(filename, 0, null).use { legacy ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    legacy.execSQL(entity.getString("createSql").replace(
                        "\${TABLE_NAME}", entity.getString("tableName"),
                    ))
                }
                legacy.execSQL("INSERT INTO dog VALUES (50, 3, 75, 1, 1)")
                legacy.execSQL("INSERT INTO user VALUES (1, 'Saved', 'avatar', 'frame', 25, 3, 2, 50)")
                legacy.execSQL("INSERT INTO history VALUES (1, 'Match', 1234, 'a', 'b', 'solo')")
                legacy.version = 1
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, filename)
                .addMigrations(AppDatabase.MIGRATION_1_2).build()
            try {
                assertEquals(25, migrated.userDao().getUser()?.coinbone)
                assertEquals(50, migrated.userDao().getUser()?.iddog)
                assertEquals(75, migrated.dogDao().getDog(50)?.progress)
                assertEquals("Match", migrated.historyDao().getHistory(1)?.nameBatter)
                val nextId = migrated.dogDao().insert(DogEntity())
                assertTrue(nextId > 50)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
    }

    @Test
    fun userAndDogUpsertsUpdateExistingRows() = runBlocking {
        assertNull(database.userDao().getUser())
        val user = UserEntity(
            name = "Player", imageAvatar = "avatar.webp", imageFrame = "frame.webp",
            coinbone = 100, rank = 2, rankbatter = 3, iddog = 7,
        )
        val dog = DogEntity(iddog = 7, stepRank = 2, progress = 45, unlock = true)
        database.userDao().upsert(user)
        database.dogDao().upsert(dog)
        assertEquals(user, database.userDao().observeUser().first())
        assertEquals(dog, database.dogDao().getDog(7))

        val updatedUser = user.copy(coinbone = 80, rankbatter = 4)
        val updatedDog = dog.copy(progress = 70, selected = true)
        database.userDao().upsert(updatedUser)
        database.dogDao().upsert(updatedDog)
        assertEquals(updatedUser, database.userDao().getUser())
        assertEquals(listOf(updatedDog), database.dogDao().observeDogs().first())

        database.userDao().delete(updatedUser)
        database.dogDao().delete(updatedDog)
        assertNull(database.userDao().getUser())
        assertNull(database.dogDao().getDog(7))
    }

    @Test
    fun historyGeneratesIdsAndOrdersNewestFirstIncludingTies() = runBlocking {
        val history = HistoryEntity(
            nameBatter = "Match", date = 1000L, imageAvatar1 = "a.webp",
            imageAvatar2 = "b.webp", typePlay = "solo",
        )
        val olderId = database.historyDao().insert(history)
        val newerId = database.historyDao().insert(history.copy(date = 2000L))
        val tiedId = database.historyDao().insert(history.copy(date = 2000L))
        assertEquals(
            listOf(tiedId, newerId, olderId),
            database.historyDao().observeHistory().first().map { it.id },
        )
        val updated = history.copy(id = olderId, nameBatter = "Updated")
        database.historyDao().update(updated)
        assertEquals(updated, database.historyDao().getHistory(olderId))
        database.historyDao().delete(updated)
        assertNull(database.historyDao().getHistory(olderId))
        database.historyDao().clear()
        assertEquals(emptyList<HistoryEntity>(), database.historyDao().observeHistory().first())
    }

    @Test
    fun dataSurvivesClosingAndReopeningDatabase() = runBlocking {
        val filename = "room-test-${UUID.randomUUID()}.db"
        val user = UserEntity(name = "Saved player", coinbone = 25)
        try {
            val first = Room.databaseBuilder(context, AppDatabase::class.java, filename).build()
            try {
                first.userDao().upsert(user)
            } finally {
                first.close()
            }
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, filename).build()
            try {
                assertEquals(user, reopened.userDao().getUser())
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
    }
}
