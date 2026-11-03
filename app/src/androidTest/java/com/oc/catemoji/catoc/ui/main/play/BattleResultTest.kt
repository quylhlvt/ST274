package com.oc.catemoji.catoc.ui.main.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.ProfileRepository
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import com.oc.catemoji.catoc.data.repository.DogSkinRepository
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.FRAME
import com.oc.catemoji.catoc.data.model.DogSkinCatalogue
import com.oc.catemoji.catoc.RoomLoadState
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
            assertTrue(checkNotNull(db.appearanceDao().getState(FRAME, "2")).unlock)
            assertTrue(checkNotNull(db.appearanceDao().getState(FRAME, "3")).unlock)
            assertEquals(1L, db.dogDao().getDog(original.iddog)?.matchesPlayed)
            assertEquals(1L, db.dogDao().getDog(original.iddog)?.matchesWon)
            assertEquals(33, db.dogDao().getDog(original.iddog)?.progress)
            assertEquals(listOf(1, 6), result.badges().map { it.id })
            val profile = ProfileRepository(db)
            assertEquals(1, profile.statistics.first().wins)
            assertEquals(result.badges(), profile.achievements.first())
            assertEquals(db.userDao().getUser(), repo.user.first())
            result.badges().forEach { badge -> context.assets.open(badge.image.removePrefix("file:///android_asset/")).close() }
            coroutineScope { List(4) { async { repo.claimBattleBonus("same-match") } }.awaitAll() }
            assertEquals(300, db.userDao().getUser()?.coinbone)
            assertEquals(1L, db.dogDao().getDog(original.iddog)?.matchesPlayed)
            assertEquals(1, db.historyDao().count())
            assertTrue(repo.completeBattle("same-match", false, "Other", "", 0, 1).bonusClaimed)
            val lost = repo.completeBattle("next-match", false, "Max", "b", 0, 20_000)
            assertFalse(lost.won)
            assertEquals(5, lost.bones)
            assertEquals(-10, lost.rankPoints)
            assertTrue(lost.badges().isEmpty())
            assertEquals(305, db.userDao().getUser()?.coinbone)
            assertEquals(110, db.userDao().getUser()?.rankbatter)
            assertEquals(2, db.historyDao().count())
            assertEquals(2L, db.dogDao().getDog(original.iddog)?.matchesPlayed)
            assertEquals(1L, db.dogDao().getDog(original.iddog)?.matchesWon)
            assertEquals(33, db.dogDao().getDog(original.iddog)?.progress)
            assertEquals(2, profile.statistics.first().total)
            assertEquals(1, profile.statistics.first().wins)
            assertEquals(1, profile.statistics.first().losses)
            assertEquals(50.0, profile.statistics.first().winRate, 0.001)
            assertEquals(listOf(false, true), profile.history.first().map { it.typePlay })
        } finally { db.close() }
    }

    @Test fun lossDeductsOnlyAvailableRankPointsAndRetryDoesNotDeductAgain() = runBlocking {
        val db = database()
        try {
            val repo = repository(db)
            val original = repo.createProfile("Player")
            repo.saveUser(original.copy(rankbatter = 6))
            val lost = repo.completeBattle("loss-at-floor", false, "Opponent", "", 0, 20_000)
            assertEquals(5, lost.bones)
            assertEquals(-6, lost.rankPoints)
            assertEquals(105, db.userDao().getUser()?.coinbone)
            assertEquals(0, db.userDao().getUser()?.rankbatter)
            assertEquals(lost, repo.completeBattle("loss-at-floor", false, "Opponent", "", 0, 20_000))
            assertEquals(0, db.userDao().getUser()?.rankbatter)
            val another = repo.completeBattle("loss-at-zero", false, "Opponent", "", 0, 20_000)
            assertEquals(5, another.bones)
            assertEquals(0, another.rankPoints)
            assertEquals(110, db.userDao().getUser()?.coinbone)
            assertEquals(0, db.userDao().getUser()?.rankbatter)
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
                .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8).build()
            try {
                assertEquals(45, db.userDao().getUser()?.coinbone)
                assertEquals(3, db.userDao().getUser()?.rank)
                repository(db).completeBattle("migrated", false, "Opponent", "b", 0, 1)
                assertEquals(70, db.userDao().getUser()?.coinbone)
                assertNotNull(db.battleResultDao().get("migrated"))
            } finally { db.close() }
        } finally { context.deleteDatabase(filename) }
    }

    @Test fun battleCountsForTheSkinAtStartEvenWhenAnotherSkinIsEquippedBeforeSaving() = runBlocking {
        val db = database()
        val store = ViewModelStore()
        var modelJob: Job? = null
        try {
            val repo = repository(db)
            val original = repo.createProfile("Player")
            repo.saveUser(original.copy(coinbone = 2_000))
            lateinit var model: PlayViewModel
            instrumentation.runOnMainSync {
                model = PlayViewModel(repo, context, TrainingAssetRepository(context),
                    SavedStateHandle(mapOf("matchId" to "skin-snapshot")))
                store.put("play", model)
                modelJob = model.viewModelScope.coroutineContext[Job]
            }
            withTimeout(5_000) { model.player.first { it is RoomLoadState.Success && it.data != null } }
            instrumentation.runOnMainSync { model.markBattleStarted() }
            val skins = DogSkinRepository(context, db)
            skins.purchase(3)
            skins.equip(3)
            val playing = withTimeout(5_000) {
                model.player.first { it is RoomLoadState.Success && it.data?.profile?.iddog == 3 }
            } as RoomLoadState.Success
            assertEquals(1, playing.data?.skinId)
            assertEquals("file:///android_asset/1/state/puppy/2.webp", playing.data?.characterImage)
            instrumentation.runOnMainSync { model.finishBattle(true) }
            withTimeout(5_000) { model.result.first { it != null } }
            assertEquals(3, db.userDao().getUser()?.iddog)
            assertEquals(1L, db.dogDao().getDog(original.iddog)?.matchesPlayed)
            assertEquals(1L, db.dogDao().getDog(original.iddog)?.matchesWon)
            assertEquals(0L, db.dogDao().getDog(3)?.matchesPlayed)
            repo.completeBattle("with-another-skin", false, "Opponent", "", 0, 1, dogId = 3)
            assertEquals(1L, db.dogDao().getDog(3)?.matchesPlayed)
            assertEquals(0L, db.dogDao().getDog(3)?.matchesWon)
            assertEquals(0, db.dogDao().getDog(3)?.progress)
            assertEquals(33, db.dogDao().getDog(original.iddog)?.progress)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            modelJob?.join()
            db.close()
        }
    }

    @Test fun migrationFromSevenPreservesDogsAndAddsDurablePerSkinStatistics() = runBlocking {
        val filename = "skin-statistics-migration-${UUID.randomUUID()}.db"
        try {
            val schema = instrumentation.context.assets.open(
                "com.oc.catemoji.catoc.data.datalocal.room.AppDatabase/7.json",
            ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            context.openOrCreateDatabase(filename, 0, null).use { legacy ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                legacy.execSQL("INSERT INTO dog VALUES (1, 2, 45, 1, 1)")
                legacy.execSQL("INSERT INTO user VALUES (1, 'Saved', 'avatar', 'frame', 125, 3, 201, 1)")
                legacy.version = 7
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, filename)
                .addMigrations(AppDatabase.MIGRATION_7_8).build()
            try {
                val dog = checkNotNull(db.dogDao().getDog(1))
                assertEquals(45, dog.progress)
                assertEquals(2, dog.stepRank)
                assertTrue(dog.unlock && dog.selected)
                assertEquals(0L, dog.matchesPlayed)
                assertEquals(0L, dog.matchesWon)
                repository(db).completeBattle("new-counted-match", true, "Opponent", "", 0, 1)
                assertEquals(225, db.userDao().getUser()?.coinbone)
            } finally { db.close() }
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, filename).build()
            try {
                assertEquals(1L, reopened.dogDao().getDog(1)?.matchesPlayed)
                assertEquals(1L, reopened.dogDao().getDog(1)?.matchesWon)
                assertEquals(33, reopened.dogDao().getDog(1)?.progress)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(filename) }
    }

    @Test fun eachSkinPersistsWinsTowardsItsOwnFixedTargetWithoutLosingProgress() = runBlocking<Unit> {
        val db = database()
        try {
            val repo = repository(db)
            repo.createProfile("Player")
            val options = listOf(1, 2, 25).map { checkNotNull(DogSkinCatalogue.findById(it)) }
            options.forEach { skin ->
                val wins = skin.winsToMaxLevel - 1L
                repo.saveDog(DogEntity(skin.id, unlock = true, matchesPlayed = wins, matchesWon = wins))
                repo.completeBattle("loss-${skin.id}", false, "Opponent", "", 0, 1, skin.id)
                val before = checkNotNull(db.dogDao().getDog(skin.id))
                assertEquals(wins, before.matchesWon)
                assertTrue(before.progress < 100)
                repo.completeBattle("max-${skin.id}", true, "Opponent", "", 0, 1, skin.id)
                val max = checkNotNull(db.dogDao().getDog(skin.id))
                assertEquals(skin.winsToMaxLevel.toLong(), max.matchesWon)
                assertEquals(100, max.progress)
                repo.completeBattle("max-${skin.id}", true, "Opponent", "", 0, 1, skin.id)
                assertEquals(max, db.dogDao().getDog(skin.id))
                repo.completeBattle("after-max-${skin.id}", false, "Opponent", "", 0, 1, skin.id)
                assertEquals(100, db.dogDao().getDog(skin.id)?.progress)
            }
            options.forEach { skin ->
                assertEquals(skin.winsToMaxLevel.toLong(), db.dogDao().getDog(skin.id)?.matchesWon)
            }
        } finally { db.close() }
    }
}
