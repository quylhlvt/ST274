package com.oc.catemoji.catoc.ui.main.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.repository.BattleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeBoneRewardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun directRewardGrantsExactly100AndIgnoresDuplicateRequestsWhileSaving() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val repository = BattleRepository(db, db.userDao(), db.dogDao(), db.historyDao())
            repository.createProfile("Reward test")
            lateinit var model: HomeViewModel
            instrumentation.runOnMainSync {
                model = HomeViewModel(SavedStateHandle())
                store.put("home", model)
            }
            assertEquals(100, db.userDao().getUser()!!.coinbone)
            val writeStarted = CompletableDeferred<Unit>()
            val allowWrite = CompletableDeferred<Unit>()
            var deliveries = 0
            val award: suspend (Int) -> Unit = { amount ->
                deliveries++
                assertEquals(100, amount)
                writeStarted.complete(Unit)
                allowWrite.await()
                repository.addBones(amount)
            }
            instrumentation.runOnMainSync {
                model.claimBoneReward(award)
                model.claimBoneReward(award)
                assertTrue(model.boneRewardBusy.value)
            }
            withTimeout(5_000) { writeStarted.await() }
            allowWrite.complete(Unit)
            withTimeout(5_000) { model.boneRewardBusy.first { !it } }
            assertEquals(1, deliveries)
            assertEquals(200, db.userDao().getUser()!!.coinbone)
            assertFalse(model.boneRewardSaveFailed.value)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            db.close()
        }
    }

    @Test fun failedRewardWriteShowsErrorAndClearsBusyState() = runBlocking {
        val store = ViewModelStore()
        lateinit var model: HomeViewModel
        try {
            instrumentation.runOnMainSync {
                model = HomeViewModel(SavedStateHandle())
                store.put("home", model)
                model.claimBoneReward { error("Write failed") }
            }
            withTimeout(5_000) { model.boneRewardSaveFailed.first { it } }
            assertFalse(model.boneRewardBusy.value)
            instrumentation.runOnMainSync { model.consumeBoneRewardError() }
            assertFalse(model.boneRewardSaveFailed.value)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
