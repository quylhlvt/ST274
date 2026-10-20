package com.oc.catemoji.catoc.ui.main.pause

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.lvt.ads.util.AppOpenManager
import com.oc.catemoji.catoc.MyApplication
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.ColorModel
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.ui.main.customize.CustomizeActivity
import com.oc.catemoji.catoc.ui.main.done.DoneActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PauseChallengeDoneFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun nextExportsConfirmedLayersWhilePauseKeepsPreviewAndTryAgainRestarts() = withChallenge {
        val challenge = resumed<PauseOCChallengeActivity>()
        await { model(challenge).displayedBitmaps.size == 1 }
        val expected = capturePreview(challenge)
        click(challenge, R.id.btnPlayOrPause)
        click(challenge, R.id.rlCharacterPause)
        await { model(challenge).session?.stageIndex == 1 && model(challenge).displayedBitmaps.size == 2 }
        val playingPreview = capturePreview(challenge)
        click(challenge, R.id.btnPlayOrPause)
        await { challenge.findViewById<View>(R.id.btnActionBarRightText).visibility == View.VISIBLE }
        val pausedPreview = capturePreview(challenge)
        assertTrue("Pause must preserve the visible preview", playingPreview.sameAs(pausedPreview))
        assertFalse("The unfinished layer must be distinguishable from the confirmed image", expected.sameAs(pausedPreview))
        instrumentation.runOnMainSync {
            assertEquals(1, model(challenge).session!!.stageIndex)
            assertEquals(2, model(challenge).displayedBitmaps.size)
        }
        click(challenge, R.id.btnActionBarRightText)
        val done = resumed<DoneActivity>()
        val result = BitmapFactory.decodeFile(done.intent.getStringExtra(DoneActivity.EXTRA_IMAGE_PATH))
        assertTrue("Next must exclude the current unconfirmed layer", expected.sameAs(result))
        await { done.findViewById<ImageView>(R.id.imgResult).drawable != null }
        assertEquals(TEMPLATE_ID, done.intent.getStringExtra(CustomizeActivity.ARG_TEMPLATE_ID))

        click(done, R.id.btnTryAgain)
        val replay = resumed<PauseOCChallengeActivity>()
        await { model(replay).session != null && model(replay).displayedBitmaps.isNotEmpty() }
        instrumentation.runOnMainSync {
            assertNotSame(challenge, replay)
            assertEquals(TEMPLATE_ID, model(replay).session!!.template.id)
            assertEquals(0, model(replay).session!!.stageIndex)
            assertEquals(1, model(replay).displayedBitmaps.size)
            assertNull(model(replay).resultPath)
            replay.finish()
        }
    }

    @Test fun selectingTheLastNavigationAutomaticallyOpensDoneWithTheImage() = withChallenge {
        val challenge = resumed<PauseOCChallengeActivity>()
        await { model(challenge).displayedBitmaps.size == 1 }
        click(challenge, R.id.btnPlayOrPause)
        click(challenge, R.id.rlCharacterPause)
        await { model(challenge).session?.stageIndex == 1 && model(challenge).displayedBitmaps.size == 2 }
        val expected = capturePreview(challenge)
        click(challenge, R.id.rlCharacterPause)
        val done = resumed<DoneActivity>()
        assertTrue(File(done.intent.getStringExtra(DoneActivity.EXTRA_IMAGE_PATH)!!).isFile)
        val result = BitmapFactory.decodeFile(done.intent.getStringExtra(DoneActivity.EXTRA_IMAGE_PATH))
        assertTrue("Completing the last nav must retain the final layer", expected.sameAs(result))
        await { done.findViewById<ImageView>(R.id.imgResult).drawable != null }
        instrumentation.runOnMainSync {
            assertTrue(challenge.isFinishing || challenge.isDestroyed)
            done.finish()
        }
    }

    private fun withChallenge(block: () -> Unit) {
        val files = listOf(Color.MAGENTA, Color.CYAN).mapIndexed { index, color ->
            File(context.cacheDir, "pause_done_flow_test_$index.png").also { file ->
                val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        }
        var shared: ViewModelActivity? = null
        var originals = emptyList<CustomModel>()
        instrumentation.runOnMainSync {
            AppOpenManager.getInstance().disableAppResumeWithActivity(DoneActivity::class.java)
            AppOpenManager.getInstance().disableAppResumeWithActivity(PauseOCChallengeActivity::class.java)
            context.startActivity(Intent(context, DoneActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DoneActivity.EXTRA_IMAGE_PATH, files.first().absolutePath)
                putExtra(CustomizeActivity.ARG_TEMPLATE_ID, TEMPLATE_ID)
            })
        }
        try {
            val bootstrap = resumed<DoneActivity>()
            instrumentation.runOnMainSync {
                shared = (bootstrap.application as MyApplication).sharedViewModel(bootstrap)
            }
            await { shared!!.localDataReady.value && !shared!!.isFetchingOnlineFlow.value }
            instrumentation.runOnMainSync {
                originals = shared!!.templates.value.filter { it.id.startsWith("online_") }
                val fixture = CustomModel(id = TEMPLATE_ID, listPath = ArrayList((1..2).map { index ->
                    BodyPartModel(nav = "nav$index", position = index, zIndex = index,
                        listPath = arrayListOf(ColorModel(listPath = arrayListOf(files[index - 1].absolutePath))))
                }))
                shared!!.appDataManager.prependOnlineTemplates(listOf(fixture) + originals)
            }
            click(resumed<DoneActivity>(), R.id.btnTryAgain)
            block()
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .toList().filter { it is DoneActivity || it is PauseOCChallengeActivity }.forEach { it.finish() }
                shared?.appDataManager?.prependOnlineTemplates(originals)
            }
            files.forEach { it.delete() }
        }
    }

    private fun capturePreview(activity: PauseOCChallengeActivity): Bitmap {
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            val preview = activity.findViewById<View>(R.id.rlCharacterPause)
            bitmap = Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888)
            preview.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun model(activity: PauseOCChallengeActivity) =
        ViewModelProvider(activity)[PauseChallengeViewModel::class.java]

    private fun click(activity: android.app.Activity, id: Int) {
        // App click listeners share a 200 ms debounce across controls.
        Thread.sleep(250)
        instrumentation.runOnMainSync {
            val view = activity.findViewById<View>(id)
            assertTrue("Control $id must be visible and enabled", view.isShown && view.isEnabled)
            assertTrue(view.performClick())
        }
    }

    private inline fun <reified T : android.app.Activity> resumed(): T {
        var activity: T? = null
        await {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<T>().firstOrNull()
            activity != null
        }
        return activity!!
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for challenge navigation")
    }

    companion object {
        private const val TEMPLATE_ID = "online_pause_done_flow_test"
    }
}
