package com.oc.catemoji.catoc.ui.main.editProfile

import android.content.res.Configuration
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.AVATAR
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.FRAME
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.ProfileAppearanceRepository
import com.oc.catemoji.catoc.databinding.ActivityEditProfileBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditProfileTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: AppDatabase
    private lateinit var repository: BattleRepository
    private val stores = mutableListOf<ViewModelStore>()
    private val appearances get() = ProfileAppearanceRepository(context, database)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { stores.forEach { it.clear() } }
        database.close()
    }

    @Test
    fun savePreservesConcurrentCoinAndRankUpdatesAndSelectedDog() = runBlocking {
        val original = repository.createProfile("Player")
        val model = model()
        val loaded = await(model) { it.ready }
        assertEquals("Player", loaded.name)
        assertEquals(15, loaded.avatars.size)
        assertEquals((1..15).map { "file:///android_asset/$it/avatar.webp" }, loaded.avatars)
        assertEquals((1..15).map { "file:///android_asset/$it/frame.webp" }, loaded.frames)

        assertEquals(setOf(loaded.avatars[0]), loaded.unlockedAvatars)
        assertEquals(setOf(loaded.frames[0]), loaded.unlockedFrames)
        appearances.unlockAvatar(loaded.avatars[1])
        appearances.unlockFrame(loaded.frames[2])
        await(model) { loaded.avatars[1] in it.unlockedAvatars && loaded.frames[2] in it.unlockedFrames }
        onMain {
            model.setName("   Buddy   ")
            model.selectAvatar(loaded.avatars[1])
            model.selectFrame(loaded.frames[2])
        }
        // Progress changes after the editor loaded must survive its eventual save.
        repository.addBones(100)
        repository.addBattleRank(50)
        onMain { model.save(); model.save() }
        await(model) { it.saved }
        assertEquals(original.copy(
            name = "Buddy", imageAvatar = loaded.avatars[1], imageFrame = loaded.frames[2],
            coinbone = original.coinbone + 100, rankbatter = original.rankbatter + 50,
        ), database.userDao().getUser())
        assertEquals("2", database.appearanceDao().getStates(AVATAR).single { it.selected }.assetId)
        assertEquals("3", database.appearanceDao().getStates(FRAME).single { it.selected }.assetId)
        assertEquals(original.iddog, database.dogDao().observeDogs().first().single().iddog)
        assertTrue(database.historyDao().observeHistory().first().isEmpty())
    }

    @Test
    fun blankNameAndUnknownAssetsCannotChangeStoredProfile() = runBlocking {
        val original = repository.createProfile("Player")
        val model = model()
        val loaded = await(model) { it.ready }
        onMain {
            model.selectAvatar("missing.webp")
            model.selectFrame("missing.webp")
            model.setName("  \n  ")
            model.save()
        }
        assertTrue(model.state.value.nameRequired)
        assertFalse(model.state.value.saving)
        assertEquals(loaded.avatar, model.state.value.avatar)
        assertEquals(loaded.frame, model.state.value.frame)
        assertEquals(original, database.userDao().getUser())
    }

    @Test
    fun lockedAvatarsAndFramesCannotBeSelectedOrApplied() = runBlocking {
        val original = repository.createProfile("Player")
        val model = model()
        val loaded = await(model) { it.ready }
        onMain {
            model.selectAvatar(loaded.avatars[1])
            model.selectFrame(loaded.frames[1])
        }
        assertEquals(original.imageAvatar, model.state.value.avatar)
        assertEquals(original.imageFrame, model.state.value.frame)
        listOf(loaded.avatars[1] to original.imageFrame, original.imageAvatar to loaded.frames[1]).forEach { (avatar, frame) ->
            try {
                repository.updateProfile("Changed", avatar, frame)
                fail("Locked appearances must not be applied")
            } catch (expected: IllegalStateException) {
                assertEquals(original, database.userDao().getUser())
            }
        }
        assertEquals(listOf(AppearanceStateEntity(AVATAR, "1", selected = true, unlock = true)),
            database.appearanceDao().getStates(AVATAR))
        assertEquals(listOf(AppearanceStateEntity(FRAME, "1", selected = true, unlock = true)),
            database.appearanceDao().getStates(FRAME))
    }

    @Test
    fun restoredDraftDoesNotWriteUntilSaved() = runBlocking {
        val original = repository.createProfile("Player")
        val handle = SavedStateHandle()
        val model = model(handle)
        val loaded = await(model) { it.ready }
        appearances.unlockAvatar(loaded.avatars.last())
        appearances.unlockFrame(loaded.frames[1])
        await(model) { loaded.avatars.last() in it.unlockedAvatars && loaded.frames[1] in it.unlockedFrames }
        onMain {
            model.setName("Draft")
            model.selectAvatar(loaded.avatars.last())
            model.selectFrame(loaded.frames[1])
        }
        var restoredHandle: SavedStateHandle? = null
        onMain { restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }) }
        val restored = await(model(restoredHandle!!)) { it.ready }
        assertEquals("Draft", restored.name)
        assertEquals(loaded.avatars.last(), restored.avatar)
        assertEquals(loaded.frames[1], restored.frame)
        assertEquals(original, database.userDao().getUser())
        assertEquals("1", database.appearanceDao().getStates(AVATAR).single { it.selected }.assetId)
        assertEquals("1", database.appearanceDao().getStates(FRAME).single { it.selected }.assetId)
    }

    @Test
    fun failedSaveRetainsDraftAndCanBeRetried() = runBlocking {
        repository.createProfile("Player")
        val model = model()
        val loaded = await(model) { it.ready }
        appearances.unlockAvatar(loaded.avatars[1])
        appearances.unlockFrame(loaded.frames[2])
        await(model) { loaded.avatars[1] in it.unlockedAvatars && loaded.frames[2] in it.unlockedFrames }
        onMain { model.selectAvatar(loaded.avatars[1]); model.selectFrame(loaded.frames[2]) }
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_profile BEFORE UPDATE ON user BEGIN SELECT RAISE(ABORT, 'test failure'); END",
        )
        onMain { model.setName("Retry"); model.save() }
        val failed = await(model) { it.error != null }
        assertFalse(failed.saving)
        assertFalse(failed.saved)
        assertEquals("Retry", failed.name)
        assertEquals("Player", database.userDao().getUser()?.name)
        assertEquals("1", database.appearanceDao().getStates(AVATAR).single { it.selected }.assetId)
        assertEquals("1", database.appearanceDao().getStates(FRAME).single { it.selected }.assetId)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_profile")
        onMain { model.save() }
        await(model) { it.saved }
        assertEquals("Retry", database.userDao().getUser()?.name)
    }

    @Test
    fun missingUserIsReportedAndNeverCreatedByEditing() = runBlocking {
        val model = model()
        val failed = await(model) { !it.loading }
        assertFalse(failed.ready)
        assertNotNull(failed.error)
        onMain { model.setName("Name"); model.save() }
        assertNull(database.userDao().getUser())
        try {
            repository.updateProfile("Name", "a", "b")
            fail("Editing must not create a missing profile")
        } catch (expected: IllegalStateException) {
            assertNull(database.userDao().getUser())
        }
    }

    @Test
    fun rowsHaveVisibleSelectedItemsOnPhoneAndTablet() = runBlocking {
        val options = appearances.loadOptions()
        onMain {
            listOf(360 to 740, 800 to 1100).forEach { (widthDp, heightDp) ->
                val configuration = Configuration(context.resources.configuration).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    smallestScreenWidthDp = widthDp
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Base_Theme_BaseActivity)
                val binding = ActivityEditProfileBinding.inflate(android.view.LayoutInflater.from(themed))
                val avatar = ProfileAvatarAdapter {}.apply { render(options.avatars, options.avatars[0], true, setOf(options.avatars[0])) }
                val frame = ProfileFrameAdapter {}.apply { render(options.frames, options.frames[1], true, setOf(options.frames[1])) }
                binding.recycleAvatar.adapter = avatar
                binding.recycleBorderFrame.adapter = frame
                val density = themed.resources.displayMetrics.density
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY),
                )
                binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
                listOf(binding.recycleAvatar to 0, binding.recycleBorderFrame to 1).forEach { (list, selected) ->
                    assertTrue(list.height > 0)
                    val item = checkNotNull(list.findViewHolderForAdapterPosition(selected)).itemView
                    assertTrue(item.width > 0)
                    assertTrue(item.findViewById<View>(R.id.imgAvatar).height > 0)
                    assertEquals(View.VISIBLE, item.findViewById<View>(R.id.stokerSelect).visibility)
                    assertEquals(View.GONE, item.findViewById<View>(R.id.key).visibility)
                    assertTrue(item.isSelected)
                }
                val lockedFrame = checkNotNull(binding.recycleBorderFrame.findViewHolderForAdapterPosition(0)).itemView
                assertEquals(View.VISIBLE, lockedFrame.findViewById<View>(R.id.key).visibility)
                assertFalse(lockedFrame.isEnabled)
                val lockedAvatar = checkNotNull(binding.recycleAvatar.findViewHolderForAdapterPosition(1)).itemView
                assertEquals(View.VISIBLE, lockedAvatar.findViewById<View>(R.id.key).visibility)
                assertFalse(lockedAvatar.isEnabled)
                assertTrue(binding.btnSave.height > 0)
            }
        }
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()): EditProfileViewModel {
        lateinit var model: EditProfileViewModel
        onMain {
            model = EditProfileViewModel(repository, appearances, handle)
            stores += ViewModelStore().apply { put("editor", model) }
        }
        return model
    }

    private suspend fun await(model: EditProfileViewModel, predicate: (EditProfileUiState) -> Boolean) =
        withTimeout(5_000) { model.state.first(predicate) }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
}
