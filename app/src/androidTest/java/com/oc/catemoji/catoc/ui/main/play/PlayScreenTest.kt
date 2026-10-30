package com.oc.catemoji.catoc.ui.main.play

import android.content.res.Configuration
import android.content.Intent
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.core.custom.VoiceTrackDivider
import com.oc.catemoji.catoc.core.custom.BattleAvatarGrowth
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.ProfileAppearanceRepository
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import com.oc.catemoji.catoc.databinding.ActivityPlayBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeNotNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun savedPlayerCannotTapUntilFlashAndCountdownFinish() {
        ActivityScenario.launch<PlayActivity>(
            Intent(context, PlayActivity::class.java).putExtra(PlayActivity.EXTRA_USE_MICROPHONE, false),
        ).use { scenario ->
            lateinit var model: PlayViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[PlayViewModel::class.java]
                assertEquals("Shared action bar must start at the same top as other screens",
                    0, it.findViewById<View>(R.id.main).paddingTop)
                assertEquals(0, it.findViewById<View>(R.id.layoutActionBar).top)
            }
            val player = runBlocking {
                withTimeout(5_000) { model.player.first { it is RoomLoadState.Success } }
            } as RoomLoadState.Success
            // Launching Play requires a saved profile; never create or alter a real user's profile here.
            assumeNotNull(player.data)
            val labels = linkedSetOf<String>()
            val deadline = SystemClock.elapsedRealtime() + 20_000L
            var started = false
            while (!started && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { activity ->
                    val avatar = activity.findViewById<View>(R.id.avatar1)
                    val countdown = activity.findViewById<android.widget.TextView>(R.id.txtStartNumber)
                    if (!avatar.isEnabled) {
                        avatar.performClick()
                        assertEquals("Early taps must not score", 0, model.barkCount.value)
                        assertEquals(MicrophoneStatus.IDLE, model.microphone.value.status)
                        if (countdown.visibility == View.VISIBLE) labels.add(countdown.text.toString())
                    } else {
                        assertEquals(View.INVISIBLE, countdown.visibility)
                        avatar.performClick()
                        assertEquals(1, model.barkCount.value)
                        started = true
                    }
                }
                SystemClock.sleep(50)
            }
            assertTrue("Battle must become playable after START", started)
            assertEquals(listOf("3", "2", "1", context.getString(R.string.play_countdown_start)), labels.toList())
        }
    }

    @Test
    fun tapBattleTrackShowsInlineAvatarWithoutWaveformBars() {
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.Base_Theme_BaseActivity)
            val binding = ActivityPlayBinding.inflate(LayoutInflater.from(themed))

            assertEquals(1f, binding.dualWaveform.alpha, 0f)
            assertEquals(View.VISIBLE, binding.imgBgVoice.visibility)
            assertEquals(View.VISIBLE, binding.bgVoiceAvatar1.visibility)
            assertEquals(View.VISIBLE, binding.bgVoiceAvatar2.visibility)
            val waveform = binding.dualWaveform
            waveform.measure(
                View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(192, View.MeasureSpec.EXACTLY),
            )
            waveform.layout(0, 0, 960, 192)
            waveform.showStaticWaveform()
            waveform.addRealAmplitude(32767)
            val bitmap = Bitmap.createBitmap(960, 192, Bitmap.Config.ARGB_8888)
            try {
                waveform.draw(Canvas(bitmap))
                assertTrue("Avatar remains visible", bitmap.getPixel(480, 96) ushr 24 != 0)
                assertEquals("No player bars", 0, bitmap.getPixel(100, 96))
                assertEquals("No opponent bars", 0, bitmap.getPixel(860, 96))
            } finally {
                waveform.stop()
                bitmap.recycle()
            }
        }
    }

    @Test
    fun avatarsTouchAtFullProgressAndReturnToTheirOriginalSizeAtTheCenter() {
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.Base_Theme_BaseActivity)
            val binding = ActivityPlayBinding.inflate(LayoutInflater.from(themed))
            val growth = BattleAvatarGrowth(binding.avatar1, binding.avatar2)
            try {
                listOf(1080, 1440).forEach { width ->
                    binding.root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
                    )
                    binding.root.layout(0, 0, width, 2400)
                    growth.updateBattleProgress(0.75f)
                    assertTrue(binding.avatar1.scaleX > 1f)
                    assertEquals(1f, binding.avatar2.scaleX, 0.001f)
                    growth.updateBattleProgress(1f)
                    assertEquals(binding.avatar2.left.toFloat(),
                        binding.avatar1.left + binding.avatar1.width * binding.avatar1.scaleX, 0.5f)
                    growth.updateBattleProgress(0f)
                    assertEquals(binding.avatar1.right.toFloat(),
                        binding.avatar2.right - binding.avatar2.width * binding.avatar2.scaleX, 0.5f)
                    growth.updateBattleProgress(0.5f)
                    assertEquals(1f, binding.avatar1.scaleX, 0.001f)
                    assertEquals(1f, binding.avatar2.scaleX, 0.001f)
                }
                binding.dualWaveform.setOnBattleProgressChangedListener(growth::updateBattleProgress)
                repeat(25) { binding.dualWaveform.addPlayerPoint() }
                binding.dualWaveform.stop()
                assertTrue(binding.dualWaveform.isBattleFinished)
                val finalScale = binding.avatar1.scaleX
                binding.dualWaveform.addOpponentPoint()
                binding.dualWaveform.stop()
                assertEquals(finalScale, binding.avatar1.scaleX, 0.001f)
            } finally {
                binding.dualWaveform.setOnBattleProgressChangedListener(null)
                binding.dualWaveform.stop()
                growth.dispose()
            }
        }
    }

    @Test
    fun voiceMarkerAndBothColorAreasUseTheFullTrackAfterLayoutAndResize() {
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.Base_Theme_BaseActivity)
            val binding = ActivityPlayBinding.inflate(LayoutInflater.from(themed))
            val divider = VoiceTrackDivider(
                binding.dualWaveform, binding.changeVoice,
                binding.bgVoiceAvatar1, binding.bgVoiceAvatar2,
            )
            try {
                listOf(1080, 1440).forEach { width ->
                    binding.root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
                    )
                    binding.root.layout(0, 0, width, 2400)
                    listOf(0f, 0.2f, 0.5f, 0.8f, 1f).forEach { fraction ->
                        divider.updateBattleProgress(fraction)
                        val expectedX = binding.dualWaveform.left + binding.dualWaveform.width * fraction
                        assertEquals(expectedX, binding.changeVoice.x + binding.changeVoice.width / 2f, 0.5f)
                        val playerClip = requireNotNull(binding.bgVoiceAvatar1.clipBounds)
                        val opponentClip = requireNotNull(binding.bgVoiceAvatar2.clipBounds)
                        assertEquals(expectedX, (binding.bgVoiceAvatar1.left + playerClip.right).toFloat(), 1f)
                        assertEquals(expectedX, (binding.bgVoiceAvatar2.left + opponentClip.left).toFloat(), 1f)
                        assertEquals(binding.dualWaveform.width, binding.bgVoiceAvatar1.width)
                        assertEquals(binding.dualWaveform.width, binding.bgVoiceAvatar2.width)
                    }
                }
            } finally {
                divider.dispose()
            }
        }
    }

    @Test
    fun voiceFrameLoadsDrawsAndPlayLayoutInflatesOnPhoneAndTablet() {
        instrumentation.runOnMainSync {
            listOf(360 to 740, 800 to 1100).forEach { (widthDp, heightDp) ->
                val configuration = Configuration(context.resources.configuration).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    smallestScreenWidthDp = widthDp
                }
                val themed = ContextThemeWrapper(
                    context.createConfigurationContext(configuration), R.style.Base_Theme_BaseActivity,
                )
                // An oversized vector path can compile successfully but become
                // STRING_TOO_LARGE in the APK and throw during inflation.
                val binding = ActivityPlayBinding.inflate(LayoutInflater.from(themed))
                val frame = requireNotNull(binding.imgBgVoice.foreground)
                val bitmap = Bitmap.createBitmap(333, 64, Bitmap.Config.ARGB_8888)
                try {
                    frame.setBounds(0, 0, bitmap.width, bitmap.height)
                    frame.draw(Canvas(bitmap))
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue("Voice frame must render visible pixels", pixels.any { it ushr 24 != 0 })
                    assertEquals("Voice frame must keep its centre transparent", 0, bitmap.getPixel(166, 32))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

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
                // These frames belong to the optional VS intro, which is hidden
                // in the normal battle layout. Make that owner measurable here.
                binding.contrainFlash.visibility = View.VISIBLE
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
    fun scoreStaysVisibleBelowTheVoiceTrackWithoutMicrophoneControls() {
        instrumentation.runOnMainSync {
            listOf(360 to 740, 800 to 1100).forEach { (widthDp, heightDp) ->
                val configuration = Configuration(context.resources.configuration).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    smallestScreenWidthDp = widthDp
                }
                val themed = ContextThemeWrapper(
                    context.createConfigurationContext(configuration), R.style.Base_Theme_BaseActivity,
                )
                val binding = ActivityPlayBinding.inflate(LayoutInflater.from(themed))
                val density = themed.resources.displayMetrics.density
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY),
                )
                binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)

                assertTrue(binding.txtNameAvatar1.width > 0)
                assertTrue(binding.txtNameAvatar2.width > 0)
                assertEquals(View.VISIBLE, binding.txtBarkCount.visibility)
                assertTrue(binding.txtBarkCount.top >= binding.txtNameAvatar1.bottom)
                assertTrue(binding.txtBarkCount.bottom <= minOf(binding.avatar1.top, binding.avatar2.top))
            }
        }
    }

    @Test
    fun playObservesSavedAppearanceNameRankAndPointsWithoutChangingProgress() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
        val store = ViewModelStore()
        val model = PlayViewModel(repository, context, TrainingAssetRepository(context))
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
