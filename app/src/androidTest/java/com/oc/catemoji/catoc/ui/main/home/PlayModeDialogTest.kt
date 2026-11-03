package com.oc.catemoji.catoc.ui.main.home

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import android.content.Intent
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.MyApplication
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.dialog.PlayModeDialog
import com.oc.catemoji.catoc.databinding.DialogPlayModeBinding
import com.oc.catemoji.catoc.ui.onboarding.permission.PermissionActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlayModeDialogTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun phoneAndTabletShowBothModesWithoutClipping() {
        instrumentation.runOnMainSync {
            listOf(320 to 640, 800 to 1280).forEach { (widthDp, heightDp) ->
                val config = Configuration(context.resources.configuration).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    smallestScreenWidthDp = widthDp
                    fontScale = 1f
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Base_Theme_BaseActivity)
                val binding = DialogPlayModeBinding.inflate(LayoutInflater.from(themed))
                val density = themed.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                val height = (heightDp * density).toInt()
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                binding.root.layout(0, 0, width, height)
                assertTrue(binding.modeContent.top >= 0)
                assertTrue(binding.modeContent.bottom <= height)
                assertTrue(binding.btnTapMode.width > 0)
                assertTrue(kotlin.math.abs(binding.btnTapMode.width - binding.btnVoiceMode.width) <= 1)
                assertTrue(binding.btnClose.top >= binding.modePanel.bottom)
                assertTrue(binding.txtModeTitle.layout.getEllipsisCount(0) == 0)
                assertTrue(binding.txtModeDescription.height >= binding.txtModeDescription.layout.height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    binding.root.draw(Canvas(bitmap))
                    File(context.cacheDir, "play-mode-$widthDp.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun permissionModelSurvivesRecreationAndModeButtonsHaveDistinctActions() {
        // Permission's legacy outlined text keeps drawing; use lifecycle polling instead of idle waiting.
        context.startActivity(Intent(context, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var activity = awaitPermissionActivity()
        try {
            var first: Any? = null
            instrumentation.runOnMainSync {
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnMicrophone).visibility)
                first = (activity.application as MyApplication).sharedPermissionViewModel(activity)
                activity.recreate()
            }
            activity = awaitPermissionActivity(activity)
            instrumentation.runOnMainSync {
                assertSame(first, (activity.application as MyApplication).sharedPermissionViewModel(activity))
            }
            listOf("tap", "voice", "close").forEach { action ->
                Thread.sleep(250) // Shared View.onClick debounce across controls.
                instrumentation.runOnMainSync {
                    val actions = mutableListOf<String>()
                    val dialog = PlayModeDialog(activity,
                        onTap = { actions.add("tap") },
                        onVoice = { actions.add("voice") },
                        onClose = { actions.add("close") },
                    )
                    dialog.show()
                    try {
                        val id = when (action) {
                            "tap" -> R.id.btnTapMode
                            "voice" -> R.id.btnVoiceMode
                            else -> R.id.btnClose
                        }
                        dialog.findViewById<View>(id).performClick()
                        assertEquals(listOf(action), actions)
                    } finally {
                        dialog.dismiss()
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun awaitPermissionActivity(previous: PermissionActivity? = null): PermissionActivity {
        repeat(100) {
            var current: PermissionActivity? = null
            instrumentation.runOnMainSync {
                current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<PermissionActivity>().firstOrNull { it !== previous }
            }
            current?.let { return it }
            Thread.sleep(100)
        }
        error("PermissionActivity did not resume")
    }
}
