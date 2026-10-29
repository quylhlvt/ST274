package com.oc.catemoji.catoc.ui.main.training

import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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
                val root = activity.findViewById<android.view.ViewGroup>(R.id.main)
                val avatar = activity.findViewById<ImageView>(R.id.imgAvatar)
                val layer = activity.findViewById<FrameLayout>(R.id.effectLayer)
                assertTrue(avatar.width > 0 && avatar.height > 0)
                avatar.performClick()
                assertEquals(1, layer.childCount)
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
                assertEquals(6, activity.findViewById<FrameLayout>(R.id.effectLayer).childCount)
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
