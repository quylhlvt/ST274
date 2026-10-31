package com.oc.catemoji.catoc.ui.main.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.ProfileRepository
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BattleResultTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun database() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private fun repository(db: AppDatabase) = BattleRepository(db, db.userDao(), db.dogDao(), db.historyDao())

    @Test fun concurrentResultRetriesAndBonusCallbacksGrantExactlyOnce() = runBlocking {
        val db = database()
        try {
            val repo = repository(db)
            val original = repo.createProfile("Player")
            repo.saveUser(original.copy(rankbatter = 90))
            val results = coroutineScope { List(4) { async {
                repo.completeBattle("same-match", true, "Buddy", "file:///android_asset/2/avatar.webp", 30, 9_000)
            } }.awaitAll() }
            assertTrue(results.all { it == results.first() })
            val result = results.first()
            assertEquals(1, db.historyDao().count())
            assertEquals(200, db.userDao().getUser()?.coinbone)
            assertEquals(120, db.userDao().getUser()?.rankbatter)
            assertEquals(2, db.userDao().getUser()?.rank)
            assertEquals(listOf(1, 6), result.badges().map { it.id })
            val profile = ProfileRepository(db)
            assertEquals(1, profile.statistics.first().wins)
            assertEquals(result.badges(), profile.achievements.first())
            assertEquals(db.userDao().getUser(), repo.user.first())
            result.badges().forEach { badge -> context.assets.open(badge.image.removePrefix("file:///android_asset/")).close() }
            coroutineScope { List(4) { async { repo.claimBattleBonus("same-match") } }.awaitAll() }
            assertEquals(300, db.userDao().getUser()?.coinbone)
            assertEquals(1, db.historyDao().count())
            assertTrue(repo.completeBattle("same-match", false, "Other", "", 0, 1).bonusClaimed)
            val lost = repo.completeBattle("next-match", false, "Max", "b", 0, 20_000)
            assertFalse(lost.won)
            assertTrue(lost.badges().isEmpty())
            assertEquals(325, db.userDao().getUser()?.coinbone)
            assertEquals(130, db.userDao().getUser()?.rankbatter)
            assertEquals(2, db.historyDao().count())
            assertEquals(2, profile.statistics.first().total)
            assertEquals(1, profile.statistics.first().wins)
            assertEquals(1, profile.statistics.first().losses)
            assertEquals(50.0, profile.statistics.first().winRate, 0.001)
            assertEquals(listOf(false, true), profile.history.first().map { it.typePlay })
        } finally { db.close() }
    }

    @Test fun viewModelRestoresTheSameResultAndContinuesBadgeRankRewardSequence() = runBlocking {
        val db = database()
        val stores = mutableListOf<ViewModelStore>()
        try {
            val repo = repository(db)
            val original = repo.createProfile("Player")
            repo.saveUser(original.copy(rankbatter = 90))
            fun newModel(state: SavedStateHandle): PlayViewModel {
                lateinit var model: PlayViewModel
                instrumentation.runOnMainSync {
                    model = PlayViewModel(repo, context, TrainingAssetRepository(context), state)
                    ViewModelStore().also { it.put("play", model); stores.add(it) }
                }
                return model
            }
            val model = newModel(SavedStateHandle(mapOf("matchId" to "restored", "barks" to 30)))
            instrumentation.runOnMainSync { model.markBattleStarted(); model.finishBattle(true); model.finishBattle(false) }
            val result = withTimeout(5_000) { model.result.first { it != null } }!!
            instrumentation.runOnMainSync {
                model.advanceResult()
                assertEquals(BattlePhase.BADGES, model.phase.value)
                model.advanceResult()
                assertEquals(BattlePhase.RANK, model.phase.value)
                model.advanceResult()
                assertEquals(BattlePhase.REWARD, model.phase.value)
            }
            val restored = newModel(SavedStateHandle(mapOf("matchId" to "restored", "won" to true,
                "phase" to BattlePhase.REWARD, "barks" to 30)))
            assertEquals(result, withTimeout(5_000) { restored.result.first { it != null } })
            assertEquals(BattlePhase.REWARD, restored.phase.value)
            assertEquals(1, db.historyDao().count())
            instrumentation.runOnMainSync { restored.claimEarnedBonus(); restored.claimEarnedBonus() }
            withTimeout(5_000) { restored.result.first { it?.bonusClaimed == true } }
            assertEquals(300, db.userDao().getUser()?.coinbone)
        } finally {
            instrumentation.runOnMainSync { stores.forEach { it.clear() } }
            db.close()
        }
    }

    @Test fun migrationFromSixPreservesProfileAndEnablesDurableResults() = runBlocking {
        val filename = "battle-migration-${UUID.randomUUID()}.db"
        try {
            val schema = instrumentation.context.assets.open(
                "com.oc.catemoji.catoc.data.datalocal.room.AppDatabase/6.json"
            ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            context.openOrCreateDatabase(filename, 0, null).use { legacy ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                legacy.execSQL("INSERT INTO user VALUES (1, 'Saved', 'avatar', 'frame', 45, 3, 201, 9)")
                legacy.version = 6
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, filename)
                .addMigrations(AppDatabase.MIGRATION_6_7).build()
            try {
                assertEquals(45, db.userDao().getUser()?.coinbone)
                assertEquals(3, db.userDao().getUser()?.rank)
                repository(db).completeBattle("migrated", false, "Opponent", "b", 0, 1)
                assertEquals(70, db.userDao().getUser()?.coinbone)
                assertNotNull(db.battleResultDao().get("migrated"))
            } finally { db.close() }
        } finally { context.deleteDatabase(filename) }
    }
}
