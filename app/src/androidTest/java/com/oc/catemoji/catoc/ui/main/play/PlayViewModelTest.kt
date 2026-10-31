package com.oc.catemoji.catoc.ui.main.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
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

    @Test fun countdownPausesRestoresAndGatesTapsUntilStart() = runBlocking {
        val state = SavedStateHandle()
        val original = model(state)
        instrumentation.runOnMainSync {
            original.onScreenStarted(false)
            original.addPlayerTap()
            assertEquals(0, original.barkCount.value)
            assertNull(original.battleUiState.value.countdownNumber)
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
        }
        withTimeout(4_000) { restored.battleUiState.first { it.canTap } }
        instrumentation.runOnMainSync {
            assertTrue(restored.battleStarted)
            assertNull(restored.battleUiState.value.countdownNumber)
            restored.addPlayerTap()
            assertEquals(1, restored.barkCount.value)
            restored.onScreenStopped(false)
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

    @Test fun gameOverAdvanceWaitsForAnActiveScreenAndKeepsAdAndSaveGuards() = runBlocking {
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
            model.onRewardedAdBusyChanged(true)
            assertFalse(model.canLeaveResult)
            assertFalse(model.canClaimBonus)
        }
        assertFalse(withTimeout(2_000) { model.resultUiState.first { it.busy } }.canLeave)
        instrumentation.runOnMainSync { model.onRewardedAdBusyChanged(false) }
        assertTrue(withTimeout(2_000) { model.resultUiState.first { !it.busy } }.canClaimBonus)
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
