package com.oc.catemoji.catoc.ui.main.home

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.dialog.GamePrompt
import com.oc.catemoji.catoc.core.dialog.GamePromptDialog
import com.oc.catemoji.catoc.databinding.DialogGamePromptBinding
import com.oc.catemoji.catoc.ui.onboarding.permission.PermissionActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GamePromptDialogTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun bothPromptLayoutsFitPhoneAndTabletAndSingleAdButtonIsCentered() {
        instrumentation.runOnMainSync {
            listOf(320 to 640, 800 to 1280).forEach { (widthDp, heightDp) ->
                val config = Configuration(context.resources.configuration).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    smallestScreenWidthDp = widthDp
                    fontScale = 1f
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Base_Theme_BaseActivity)
                GamePrompt.entries.forEach { prompt ->
                    val ads = prompt == GamePrompt.WATCH_ADS
                    val binding = DialogGamePromptBinding.inflate(LayoutInflater.from(themed)).apply {
                        txtPromptTitle.setText(if (ads) R.string.bonus_treats_title else R.string.exit_screen_title)
                        txtPromptDescription.setText(if (ads) R.string.bonus_treats_description else R.string.exit_screen_description)
                        btnPromptConfirm.setText(if (ads) R.string.watch_ads else R.string.exit)
                        imgPromptBone.isVisible = ads
                        btnPromptCancel.isVisible = !ads
                        btnPromptClose.isVisible = ads
                    }
                    val density = themed.resources.displayMetrics.density
                    val width = (widthDp * density).toInt()
                    val height = (heightDp * density).toInt()
                    binding.root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                    )
                    binding.root.layout(0, 0, width, height)
                    assertTrue(binding.promptContent.top >= 0)
                    assertTrue(binding.promptContent.bottom <= height)
                    assertTrue(binding.btnPromptConfirm.top >= binding.txtPromptDescription.bottom)
                    if (ads) assertEquals(binding.promptPanel.width / 2f,
                        (binding.btnPromptConfirm.left + binding.btnPromptConfirm.right) / 2f, 2f)
                    else assertTrue(binding.btnPromptCancel.right < binding.btnPromptConfirm.left)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        binding.root.draw(Canvas(bitmap))
                        File(context.cacheDir, "game-prompt-${prompt.name.lowercase()}-$widthDp.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    @Test fun cancelAndCloseNeverConfirmWhileWatchAndExitConfirmOnlyTheirAction() {
        context.startActivity(Intent(context, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = awaitActivity()
        try {
            GamePrompt.entries.forEach { prompt ->
                val cancelId = if (prompt == GamePrompt.EXIT) R.id.btnPromptCancel else R.id.btnPromptClose
                listOf(cancelId, R.id.btnPromptConfirm).forEach { id ->
                    Thread.sleep(250)
                    instrumentation.runOnMainSync {
                        var confirmed = 0
                        var cancelled = 0
                        val dialog = GamePromptDialog(activity, prompt, { confirmed++ }, { cancelled++ })
                        dialog.show()
                        try {
                            assertEquals(prompt == GamePrompt.WATCH_ADS,
                                dialog.findViewById<View>(R.id.btnPromptClose).isVisible)
                            assertEquals(prompt == GamePrompt.EXIT,
                                dialog.findViewById<View>(R.id.btnPromptCancel).isVisible)
                            dialog.findViewById<View>(id).performClick()
                            assertEquals(if (id == R.id.btnPromptConfirm) 1 else 0, confirmed)
                            assertEquals(if (id == cancelId) 1 else 0, cancelled)
                        } finally { dialog.dismiss() }
                    }
                }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun awaitActivity(): PermissionActivity {
        repeat(100) {
            var activity: PermissionActivity? = null
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<PermissionActivity>().firstOrNull()
            }
            activity?.let { return it }
            Thread.sleep(100)
        }
        error("PermissionActivity did not resume")
    }
}
