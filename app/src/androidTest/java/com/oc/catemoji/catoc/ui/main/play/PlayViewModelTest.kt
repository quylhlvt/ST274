package com.oc.catemoji.catoc.ui.main.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: AppDatabase
    private lateinit var repository: BattleRepository
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
        repository.createProfile("Player")
        Unit
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync { stores.forEach { it.clear() } }
        database.close()
    }

    private suspend fun model(state: SavedStateHandle = SavedStateHandle()): PlayViewModel {
        lateinit var model: PlayViewModel
        instrumentation.runOnMainSync {
            model = PlayViewModel(repository, context, TrainingAssetRepository(context), state)
            ViewModelStore().also { it.put("play", model); stores.add(it) }
        }
        withTimeout(5_000) { model.player.first { it is RoomLoadState.Success && it.data != null } }
        return model
    }

    @Test fun exitConfirmationPausesGameAndRemainsPausedAfterRecreation() = runBlocking {
        val state = SavedStateHandle(mapOf(
            "play_battle_started" to true,
            "play_intro_finished" to true,
            "play_countdown_step" to 4,
        ))
        val original = model(state)
        lateinit var snapshot: Map<String, Any?>
        instrumentation.runOnMainSync {
            original.onScreenStarted(false)
            assertTrue(original.battleUiState.value.canTap)
            assertTrue(original.requestExitConfirmation())
            assertFalse(original.requestExitConfirmation())
            assertFalse(original.battleUiState.value.canPlay)
            original.addPlayerTap()
            assertEquals(0, original.barkCount.value)
            original.onScreenStopped(false)
            snapshot = state.keys().associateWith { state.get<Any?>(it) }
        }
        val restored = model(SavedStateHandle(snapshot))
        instrumentation.runOnMainSync {
            assertTrue(restored.exitConfirmationVisible.value)
            restored.onScreenStarted(false)
            assertFalse(restored.battleUiState.value.canTap)
            restored.dismissExitConfirmation()
            restored.onScreenStarted(false)
            assertTrue(restored.battleUiState.value.canTap)
            restored.addPlayerTap()
            assertEquals(1, restored.barkCount.value)
            assertFalse(restored.isFinished)
        }
        assertEquals(0, database.historyDao().count())
    }

    @Test fun guidePreservesTheMatchAndExcludesPausedTimeAfterRecreation() = runBlocking {
        val state = SavedStateHandle()
        val original = model(state)
        lateinit var snapshot: Map<String, Any?>
        instrumentation.runOnMainSync {
            original.onScreenStarted(false)
            original.markBattleStarted()
            original.addPlayerTap()
            original.trackProgress = 0.54f
            assertTrue(original.requestGuide())
            assertFalse(original.requestGuide())
            assertFalse(original.battleUiState.value.canPlay)
            assertFalse(original.battleUiState.value.runTapOpponent)
            original.addPlayerTap()
            assertEquals(1, original.barkCount.value)
            original.onScreenStopped(false)
            snapshot = state.keys().associateWith { state.get<Any?>(it) }
        }
        delay(100)
        val restoredState = SavedStateHandle(snapshot)
        val restored = model(restoredState)
        instrumentation.runOnMainSync {
            restored.onScreenStarted(false)
            assertTrue(restored.guideVisible.value)
            assertFalse(restored.battleUiState.value.canTap)
            assertEquals(0.54f, restored.trackProgress, 0f)
            restored.dismissGuide()
            restored.onScreenStarted(false)
            assertTrue(restored.battleUiState.value.canTap)
            assertTrue(restored.battleUiState.value.runTapOpponent)
            restored.addPlayerTap()
            assertEquals(2, restored.barkCount.value)
            assertTrue(checkNotNull(restoredState.get<Long>("startedAt")) >= checkNotNull(state.get<Long>("startedAt")) + 100)
        }
    }

    @Test fun countdownPausesRestoresAndGatesTapsUntilStart() = runBlocking<Unit> {
        val state = SavedStateHandle()
        val original = model(state)
        instrumentation.runOnMainSync {
            original.onScreenStarted(false)
            original.addPlayerTap()
            assertEquals(0, original.barkCount.value)
            assertNull(original.battleUiState.value.countdownNumber)
            assertFalse(original.battleUiState.value.useActiveCharacter)
            original.onIntroFinished()
        }
        withTimeout(3_000) { original.battleUiState.first { it.countdownNumber == 2 } }
        lateinit var snapshot: Map<String, Any?>
        instrumentation.runOnMainSync {
            original.onScreenStopped(false)
            snapshot = state.keys().associateWith { state.get<Any?>(it) }
            original.addPlayerTap()
            assertEquals(0, original.barkCount.value)
        }
        delay(1_100)
        assertEquals(1, state.get<Int>("play_countdown_step"))
        val restored = model(SavedStateHandle(snapshot))
        instrumentation.runOnMainSync {
            assertTrue(restored.introFinished)
            restored.onScreenStarted(false)
            assertEquals(2, restored.battleUiState.value.countdownNumber)
            assertFalse(restored.battleUiState.value.useActiveCharacter)
        }
        withTimeout(3_000) { restored.battleUiState.first { it.countdownNumber == 0 } }
        instrumentation.runOnMainSync {
            assertTrue(restored.battleUiState.value.useActiveCharacter)
            assertFalse(restored.battleUiState.value.canTap)
            restored.onScreenStopped(false)
            assertTrue(restored.battleUiState.value.useActiveCharacter)
            restored.onScreenStarted(false)
            assertEquals(0, restored.battleUiState.value.countdownNumber)
        }
        val activePlayer = withTimeout(1_000) {
            restored.player.first { it is RoomLoadState.Success && it.data?.useActiveCharacter == true }
        } as RoomLoadState.Success
        assertTrue(activePlayer.data!!.characterImage.endsWith("/2.webp"))
        withTimeout(4_000) { restored.battleUiState.first { it.canTap } }
        instrumentation.runOnMainSync {
            assertTrue(restored.battleStarted)
            assertNull(restored.battleUiState.value.countdownNumber)
            restored.addPlayerTap()
            assertEquals(1, restored.barkCount.value)
            restored.onScreenStopped(false)
            assertTrue(restored.battleUiState.value.useActiveCharacter)
            restored.addPlayerTap()
            assertEquals(1, restored.barkCount.value)
            restored.onScreenStarted(false)
            assertTrue(restored.battleUiState.value.canTap)
            restored.finishBattle(false)
            restored.addPlayerTap()
            assertEquals(1, restored.barkCount.value)
        }
        withTimeout(5_000) { restored.result.first { it != null } }
    }

    @Test fun gameOverAdvanceWaitsForAnActiveScreenAndBonusIsGrantedDirectlyOnce() = runBlocking {
        val model = model()
        instrumentation.runOnMainSync { model.finishBattle(false) }
        withTimeout(5_000) { model.result.first { it != null } }
        delay(1_100)
        assertEquals(BattlePhase.GAME_OVER, model.phase.value)
        instrumentation.runOnMainSync { model.onScreenStarted(false); model.onScreenStopped(false) }
        delay(1_100)
        assertEquals(BattlePhase.GAME_OVER, model.phase.value)
        instrumentation.runOnMainSync { model.onScreenStarted(false) }
        withTimeout(3_000) { model.resultUiState.first { it.showReward } }
        instrumentation.runOnMainSync {
            assertTrue(model.canClaimBonus)
            model.claimEarnedBonus()
            assertFalse(model.canLeaveResult)
            assertFalse(model.canClaimBonus)
        }
        val rewarded = withTimeout(5_000) {
            model.resultUiState.first { !it.busy && it.reward?.bonusClaimed == true }
        }
        assertTrue(rewarded.canLeave)
        assertFalse(rewarded.canClaimBonus)
    }

    @Test fun matchKeepsItsSkinProgressAndBothCadencesAfterDataChangesAndRecreation() = runBlocking<Unit> {
        repository.saveDog(DogEntity(1, unlock = true, selected = true, matchesPlayed = 1, matchesWon = 1))
        val state = SavedStateHandle()
        val original = model(state)
        val ready = withTimeout(5_000) {
            original.player.first { it is RoomLoadState.Success && it.data?.skinProgressPercent == 33 }
        } as RoomLoadState.Success
        val startingPlayer = checkNotNull(ready.data)
        instrumentation.runOnMainSync { original.onScreenStarted(false); original.markBattleStarted() }
        repository.saveDog(DogEntity(1, unlock = true, matchesPlayed = 3, matchesWon = 3))
        repository.saveDog(DogEntity(2, unlock = true, selected = true, matchesPlayed = 4, matchesWon = 4))
        repository.saveUser(checkNotNull(database.userDao().getUser()).copy(iddog = 2))
        val changed = withTimeout(5_000) {
            original.player.first { it is RoomLoadState.Success && it.data?.profile?.iddog == 2 }
        } as RoomLoadState.Success
        assertEquals(1, changed.data?.skinId)
        assertEquals(33, changed.data?.skinProgressPercent)
        assertEquals(startingPlayer.tapOpponentIntervalMs, changed.data?.tapOpponentIntervalMs)
        assertEquals(startingPlayer.microphoneOpponentIntervalMs, changed.data?.microphoneOpponentIntervalMs)
        lateinit var snapshot: Map<String, Any?>
        instrumentation.runOnMainSync {
            original.onScreenStopped(false)
            snapshot = state.keys().associateWith { state.get<Any?>(it) }
        }
        val restored = model(SavedStateHandle(snapshot))
        val restoredPlayer = (restored.player.value as RoomLoadState.Success).data!!
        assertEquals(1, restoredPlayer.skinId)
        assertEquals(33, restoredPlayer.skinProgressPercent)
        assertEquals(startingPlayer.tapOpponentIntervalMs, restoredPlayer.tapOpponentIntervalMs)
        assertEquals(startingPlayer.microphoneOpponentIntervalMs, restoredPlayer.microphoneOpponentIntervalMs)
    }

    @Test fun voiceModeRequestsPermissionOnceAndRejectsTapsAndStoppedRequests() = runBlocking {
        val model = model(SavedStateHandle(mapOf(
            PlayViewModel.EXTRA_USE_MICROPHONE to true,
            "play_battle_started" to true,
            "play_intro_finished" to true,
            "play_countdown_step" to 4,
        )))
        instrumentation.runOnMainSync {
            model.onScreenStarted(false)
            model.addPlayerTap()
            assertEquals(0, model.barkCount.value)
            assertTrue(model.requestMicrophone(false))
            assertFalse(model.requestMicrophone(false))
            model.onMicrophonePermissionResult(granted = false, blocked = true)
            model.retryMicrophone()
            assertFalse(model.requestMicrophone(false))
            assertEquals(MicrophoneStatus.PERMISSION_REQUIRED, model.microphone.value.status)
            model.onScreenStopped(false)
            model.retryMicrophone()
            assertFalse(model.requestMicrophone(false))
        }
    }
}
