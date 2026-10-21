package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.lvt.ads.util.AppOpenManager
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.custom.DrawView
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.core.custom.listener.listenerdraw.DeleteEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DiyKeyboardDeletionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun tagTabShowsTheFullRemoteTagCatalogue() = withEditor { activity, _ ->
        instrumentation.runOnMainSync {
            activity.findViewById<View>(R.id.tagTag).performClick()
        }
        await {
            val tags = activity.findViewById<RecyclerView>(R.id.recycleTagKeybroad)
            tags.visibility == View.VISIBLE && tags.adapter?.itemCount == 34
        }
        instrumentation.runOnMainSync {
            assertEquals(View.GONE, activity.findViewById<View>(R.id.keyBroad).visibility)
        }
    }

    @Test fun keyboardTextStartsAboveItsKeyboardImage() = withEditor { activity, path ->
        val entry = addKey(activity, path, "A")
        await { image(entry) != null && textDraw(activity)?.textContent == "A" }
        await {
            val textTop = textDraw(activity)?.mappedBound?.top ?: return@await false
            textTop <= activity.resources.getDimension(R.dimen.dimension_16) + 1f
        }
        instrumentation.runOnMainSync {
            assertEquals(path, image(entry)!!.drawablePath)
            val textCenterY = textDraw(activity)!!.mappedCenterPoint.y
            val imageCenterY = image(entry)!!.mappedCenterPoint.y
            assertTrue("Keyboard text should be above the keyboard image", textCenterY < imageCenterY)
        }
    }

    @Test fun keyboardApiImageDropsFullyTransparentPadding() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 5 until 11) {
            for (x in 3 until 7) source.setPixel(x, y, Color.CYAN)
        }
        val result = DiyBitmapRenderer.trimTransparentPadding(source)
        assertEquals(4, result.width)
        assertEquals(6, result.height)
    }

    @Test fun canvasDeleteRemovesTheExactRepeatedCharacterAndSortItem() = withEditor { activity, path ->
        val entries = "ABA".map { addKey(activity, path, it.toString()) }
        await { entries.all { image(it) != null } && textDraw(activity)?.textContent == "ABA" }
        instrumentation.runOnMainSync {
            val view = activity.findViewById<DrawView>(R.id.drawView)
            assertEquals("ABA", textDraw(activity)!!.textContent)
            assertSortMatches(activity)
            val firstA = image(entries.first())!!
            view.selectCurrentDraw(firstA)
            // Exercise the actual canvas icon, bypassing the keyboard's delete handler.
            DeleteEvent().onActionUp(view, null)
            assertFalse(view.getDraws().contains(firstA))
            assertEquals(3, view.getDraws().size)
            assertSortMatches(activity)
        }
        await { textDraw(activity)?.textContent == "BA" }
    }

    @Test fun deletingSharedTextRemovesLinkedImagesButPreservesOtherStickers() = withEditor { activity, path ->
        val entries = "AB".map { addKey(activity, path, it.toString()) }
        await { entries.all { image(it) != null } && textDraw(activity)?.textContent == "AB" }
        instrumentation.runOnMainSync {
            val view = activity.findViewById<DrawView>(R.id.drawView)
            val other = DrawableDraw(
                Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).toDrawable(activity.resources),
                "unrelated-sticker"
            )
            view.addDraw(other)
            view.selectCurrentDraw(textDraw(activity)!!)
            DeleteEvent().onActionUp(view, null)
            assertNull(textDraw(activity))
            assertEquals(listOf(other), view.getDraws())
            assertSortMatches(activity)
        }
    }

    @Test fun deletingLastImageRemovesSharedTextAndEmptiesSortImmediately() = withEditor { activity, path ->
        val entry = addKey(activity, path, "M")
        await { image(entry) != null && textDraw(activity)?.textContent == "M" }
        instrumentation.runOnMainSync {
            val view = activity.findViewById<DrawView>(R.id.drawView)
            view.selectCurrentDraw(image(entry)!!)
            DeleteEvent().onActionUp(view, null)
            assertNull(textDraw(activity))
            assertTrue(view.getDraws().isEmpty())
            assertSortMatches(activity)
        }
    }

    @Test fun catLayerKeepsLargeScaleAndCanBeDeletedIndividually() = withEditor { activity, path ->
        val cat = addCat(activity, path)
        await { activity.findViewById<DrawView>(R.id.drawView).getDraws().contains(cat) }
        instrumentation.runOnMainSync {
            val view = activity.findViewById<DrawView>(R.id.drawView)
            assertTrue(cat.isCharacter)
            assertTrue(cat.isRemovable)
            view.selectCurrentDraw(cat)
            DeleteEvent().onActionUp(view, null)
            assertFalse(view.getDraws().contains(cat))
            assertSortMatches(activity)
        }
    }

    private fun withEditor(block: (CustomDiyPetActivity, String) -> Unit) {
        val sessionId = System.nanoTime().toString()
        val file = File(context.cacheDir, "diy-keyboard-delete-test.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        instrumentation.runOnMainSync {
            AppOpenManager.getInstance().disableAppResumeWithActivity(CustomDiyPetActivity::class.java)
            context.startActivity(
                Intent(context, CustomDiyPetActivity::class.java)
                    .putExtra(TEST_SESSION_ID, sessionId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        var editor: CustomDiyPetActivity? = null
        await {
            editor = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<CustomDiyPetActivity>()
                .firstOrNull { it.intent.getStringExtra(TEST_SESSION_ID) == sessionId }
            editor != null && editor!!.findViewById<DrawView>(R.id.drawView).width > 0
        }
        val activity = editor!!
        try {
            await { activity.findViewById<DrawView>(R.id.drawView).width > 0 }
            block(activity, file.absolutePath)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            await { activity.isDestroyed }
            file.delete()
        }
    }

    private fun addKey(activity: CustomDiyPetActivity, path: String, label: String): Any {
        lateinit var entry: Any
        instrumentation.runOnMainSync {
            val append = CustomDiyPetActivity::class.java.getDeclaredMethod("appendKeyboardText", String::class.java)
                .apply { isAccessible = true }
            entry = append.invoke(activity, label)!!
            val load = CustomDiyPetActivity::class.java.declaredMethods.first { it.name == "addImage" }
                .apply { isAccessible = true }
            val keyboardKind = load.parameterTypes[1].enumConstants!!.first { (it as Enum<*>).name == "KEYBOARD" }
            load.invoke(activity, path, keyboardKind, false, entry)
        }
        return entry
    }

    private fun addCat(activity: CustomDiyPetActivity, path: String): DrawableDraw {
        val before = activity.findViewById<DrawView>(R.id.drawView).getDraws().toSet()
        instrumentation.runOnMainSync {
            val load = CustomDiyPetActivity::class.java.declaredMethods.first { it.name == "addImage" }
                .apply { isAccessible = true }
            val catKind = load.parameterTypes[1].enumConstants!!.first { (it as Enum<*>).name == "CAT" }
            load.invoke(activity, path, catKind, false, null)
        }
        lateinit var cat: DrawableDraw
        await {
            cat = activity.findViewById<DrawView>(R.id.drawView).getDraws()
                .firstOrNull { it !in before } ?: return@await false
            true
        }
        return cat
    }

    private fun image(entry: Any): DrawableDraw? = entry.javaClass.getDeclaredField("draw")
        .apply { isAccessible = true }.get(entry) as? DrawableDraw

    private fun textDraw(activity: CustomDiyPetActivity): DrawableDraw? =
        CustomDiyPetActivity::class.java.getDeclaredField("keyboardTextDraw")
            .apply { isAccessible = true }.get(activity) as? DrawableDraw

    private fun assertSortMatches(activity: CustomDiyPetActivity) {
        val canvas = activity.findViewById<DrawView>(R.id.drawView).getDraws()
        val adapter = activity.findViewById<RecyclerView>(R.id.recycleSort).adapter!!
        val items = DiyDrawAdapter::class.java.getDeclaredField("items")
            .apply { isAccessible = true }.get(adapter)
        assertEquals(canvas.size, adapter.itemCount)
        assertEquals(canvas, items)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for the keyboard image")
    }

    private companion object {
        const val TEST_SESSION_ID = "diy_keyboard_test_session_id"
    }
}
