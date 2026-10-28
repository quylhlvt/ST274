package com.oc.catemoji.catoc.ui.main.play

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.data.audio.DeviceSoundClassifier
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.ProfileAppearanceRepository
import com.oc.catemoji.catoc.databinding.ActivityPlayBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun bothAvatarFramesHaveRuntimeImagesAndVisibleBoundsOnPhoneAndTablet() {
        instrumentation.runOnMainSync {
            listOf(360 to 740, 800 to 1100).forEach { (widthDp, heightDp) ->
                val configuration = Configuration(context.resources.configuration).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    smallestScreenWidthDp = widthDp
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Base_Theme_BaseActivity)
                val binding = ActivityPlayBinding.inflate(LayoutInflater.from(themed))
                val density = themed.resources.displayMetrics.density
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY),
                )
                binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
                listOf(binding.frameAvatar1, binding.frameAvatar2).forEach { frame ->
                    assertTrue("Avatar container must have visible dimensions", frame.width > 0 && frame.height > 0)
                }
                listOf(binding.imgAvatar1, binding.imgAvatar2, binding.imgFrameAvatar1, binding.imgFrameAvatar2)
                    .forEach { image: ImageView ->
                        assertTrue(image.width > 0 && image.height > 0)
                        assertNotNull("Preview-only tools:src leaves runtime avatars empty", image.drawable)
                    }
            }
        }
    }

    @Test
    fun playObservesSavedAppearanceNameRankAndPointsWithoutChangingProgress() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
        val store = ViewModelStore()
        val model = PlayViewModel(repository, DeviceSoundClassifier(context))
        instrumentation.runOnMainSync { store.put("play", model) }
        suspend fun awaitPlayer(predicate: (UserEntity?) -> Boolean): UserEntity? =
            (withTimeout(5_000) {
                model.player.first { it is RoomLoadState.Success && predicate(it.data) }
            } as RoomLoadState.Success).data
        try {
            assertNull(awaitPlayer { it == null })
            val original = repository.createProfile("Player")
            assertEquals(original, awaitPlayer { it != null })
            val appearances = ProfileAppearanceRepository(context, database)
            val options = appearances.loadOptions()
            appearances.unlockAvatar(options.avatars[1])
            appearances.unlockFrame(options.frames[2])
            repository.updateProfile("Buddy", options.avatars[1], options.frames[2])
            repository.addBattleRank(850)
            val updated = checkNotNull(awaitPlayer { it?.name == "Buddy" && it.rankbatter == 850 })
            assertEquals(options.avatars[1], updated.imageAvatar)
            assertEquals(options.frames[2], updated.imageFrame)
            assertEquals(original.rank, updated.rank)
            assertEquals(original.coinbone, updated.coinbone)
            assertEquals(updated, database.userDao().getUser())
            assertTrue(database.historyDao().observeHistory().first().isEmpty())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            database.close()
        }
    }
}
