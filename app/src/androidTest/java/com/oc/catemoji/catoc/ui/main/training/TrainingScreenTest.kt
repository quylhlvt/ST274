package com.oc.catemoji.catoc.ui.main.training

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import com.oc.catemoji.catoc.data.audio.BarkSpeechWord
import com.oc.catemoji.catoc.data.audio.SpeechRecognitionUpdate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class TrainingScreenTest {
    @Test
    fun catalogueIncludesAllThreeEffectFoldersAndReadableBackgrounds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = TrainingAssetRepository(context)
        val effects = repository.loadEffects()
        assertEquals(setOf(1, 2, 3), effects.map { it.folder }.toSet())
        val backgrounds = repository.loadBackgrounds()
        assertTrue(backgrounds.isNotEmpty())
        (effects.map { it.path } + backgrounds).forEach { path ->
            context.assets.open(path.removePrefix("file:///android_asset/")).use {
                assertTrue("Asset must contain image data: $path", it.read() >= 0)
            }
        }
    }

    @Test
    fun backgroundLoadsAndRepeatedTapsCreateCenteredEffectsThatAreRemoved() {
        ActivityScenario.launch(TrainingActivity::class.java).use { scenario ->
            awaitCondition(scenario) { it.findViewById<View>(R.id.main).background != null }
            lateinit var image: ImageView
            scenario.onActivity { activity ->
                assertEquals("Use the same action bar top spacing as Play and Profile", 0,
                    activity.findViewById<View>(R.id.main).paddingTop)
                assertEquals(0, activity.findViewById<View>(R.id.layoutActionBar).top)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnActionBarLeft).visibility)
                assertEquals(
                    activity.getString(R.string.training),
                    activity.findViewById<TextView>(R.id.tvCenter).text.toString(),
                )
                val root = activity.findViewById<android.view.ViewGroup>(R.id.main)
                val avatar = activity.findViewById<ImageView>(R.id.imgAvatar)
                val layer = activity.findViewById<FrameLayout>(R.id.effectLayer)
                assertTrue(avatar.width > 0 && avatar.height > 0)
                avatar.performClick()
            }
            awaitCondition(scenario) { it.findViewById<FrameLayout>(R.id.effectLayer).childCount == 1 }
            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.ViewGroup>(R.id.main)
                val avatar = activity.findViewById<ImageView>(R.id.imgAvatar)
                val layer = activity.findViewById<FrameLayout>(R.id.effectLayer)
                image = layer.getChildAt(0) as ImageView
                val avatarBounds = Rect(0, 0, avatar.width, avatar.height)
                root.offsetDescendantRectToMyCoords(avatar, avatarBounds)
                val params = image.layoutParams as FrameLayout.LayoutParams
                assertEquals(avatarBounds.centerX(), layer.left + params.leftMargin + params.width / 2)
                assertEquals(avatarBounds.centerY(), layer.top + params.topMargin + params.height / 2)
            }
            awaitCondition(scenario) { image.drawable != null }
            awaitCondition(scenario) { it.findViewById<FrameLayout>(R.id.effectLayer).childCount == 0 }
            scenario.onActivity { activity ->
                val avatar = activity.findViewById<View>(R.id.imgAvatar)
                repeat(6) { avatar.performClick() }
            }
            awaitCondition(scenario) {
                it.findViewById<TextView>(R.id.txtBarkCount).text == it.getString(R.string.play_bark_count, 7) &&
                    it.findViewById<FrameLayout>(R.id.effectLayer).childCount > 0
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                assertEquals(0, activity.findViewById<FrameLayout>(R.id.effectLayer).childCount)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.main).background)
            }
        }
    }

    @Test
    fun recognizedBarksShowEffectsWithoutCountingTheSamePartialAndFinalTwice() {
        ActivityScenario.launch(TrainingActivity::class.java).use { scenario ->
            awaitCondition(scenario) { it.findViewById<View>(R.id.main).background != null }
            val effectCount = AtomicInteger()
            scenario.onActivity { activity ->
                activity.findViewById<FrameLayout>(R.id.effectLayer).setOnHierarchyChangeListener(
                    object : ViewGroup.OnHierarchyChangeListener {
                        override fun onChildViewAdded(parent: View?, child: View?) { effectCount.incrementAndGet() }
                        override fun onChildViewRemoved(parent: View?, child: View?) = Unit
                    },
                )
                val model = ViewModelProvider(activity)[TrainingViewModel::class.java]
                val first = BarkSpeechWord("gâu", 100, 300, 0.95f)
                val second = BarkSpeechWord("gâu", 300, 500, 0.95f)
                model.acceptRecognition(SpeechRecognitionUpdate("mõ vàng giàng", true, 0.5f, listOf(
                    BarkSpeechWord("mõ", 0, 100, 1f), BarkSpeechWord("vàng", 100, 200, 1f),
                    BarkSpeechWord("giàng", 200, 300, 1f),
                )))
                assertEquals(0, model.barkCount.value)
                model.acceptRecognition(SpeechRecognitionUpdate("gâu", false, 0.5f, listOf(first)))
                assertEquals("The first word scores immediately", 1, model.barkCount.value)
                model.acceptRecognition(SpeechRecognitionUpdate("gâu gâu", false, 0.5f, listOf(first, second)))
                model.acceptRecognition(SpeechRecognitionUpdate("gâu gâu", true, 0.5f, listOf(first, second)))
                assertEquals(2, model.barkCount.value)
            }
            awaitCondition(scenario) {
                effectCount.get() == 2 &&
                    it.findViewById<TextView>(R.id.txtBarkCount).text == it.getString(R.string.play_bark_count, 2)
            }
            awaitCondition(scenario) { it.findViewById<FrameLayout>(R.id.effectLayer).childCount == 0 }
            assertEquals(2, effectCount.get())
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(2, ViewModelProvider(activity)[TrainingViewModel::class.java].barkCount.value)
                assertEquals(0, activity.findViewById<FrameLayout>(R.id.effectLayer).childCount)
            }
        }
    }

    private fun awaitCondition(
        scenario: ActivityScenario<TrainingActivity>,
        condition: (TrainingActivity) -> Boolean,
    ) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            android.os.SystemClock.sleep(20)
        }
        fail("Training screen did not reach the expected state within 5 seconds")
    }
}
