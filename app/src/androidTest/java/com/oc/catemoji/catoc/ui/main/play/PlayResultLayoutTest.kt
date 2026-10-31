package com.oc.catemoji.catoc.ui.main.play

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.model.BattleRules
import com.oc.catemoji.catoc.databinding.ActivityPlayBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlayResultLayoutTest {
    @Test fun resultControlsFitOnPhoneAndTabletAndAreHiddenBeforeTheBattleEnds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            listOf(360 to 720, 800 to 1100).forEach { (widthDp, heightDp) ->
                val config = Configuration(context.resources.configuration).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    smallestScreenWidthDp = widthDp
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Base_Theme_BaseActivity)
                val binding = ActivityPlayBinding.inflate(LayoutInflater.from(themed))
                assertFalse(binding.contrainOver.isVisible)
                binding.root.background = ContextCompat.getDrawable(themed, R.drawable.img_bg_home1)
                binding.recycleAchi.adapter = BattleBadgeAdapter().apply { submitList(listOf(BattleRules.badge(1))) }
                binding.contrainOver.isVisible = true
                val density = themed.resources.displayMetrics.density
                listOf("badges", "rank", "reward").forEach { stage ->
                    binding.achiContrain.isVisible = stage == "badges"
                    binding.rankContrain.isVisible = stage == "rank"
                    binding.completeContrain.isVisible = stage == "reward"
                    binding.root.measure(
                        View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    )
                    binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
                    val controls = when (stage) {
                        "badges" -> listOf(binding.btnNext, binding.recycleAchi, binding.txtTitleUnlock)
                        "rank" -> listOf(binding.btnNext2, binding.imgRankPre, binding.imgRankNext, binding.txtTitleUnlock2, binding.txtContent)
                        else -> listOf(binding.btnSave, binding.btnAddBone, binding.imgHomeParticipation,
                            binding.txtCoinBone, binding.txtTraining, binding.txtNameTitleParticipation, binding.txtRewardMultiplier)
                    }
                    controls.forEach { control ->
                        val rect = Rect(0, 0, control.width, control.height)
                        binding.root.offsetDescendantRectToMyCoords(control, rect)
                        assertTrue("$stage control must have bounds", rect.width() > 0 && rect.height() > 0)
                        assertTrue("$stage control must fit horizontally: $rect", rect.left >= 0 && rect.right <= binding.root.width)
                        assertTrue("$stage control must fit vertically: $rect", rect.top >= 0 && rect.bottom <= binding.root.height)
                        if (control is android.widget.TextView) {
                            assertTrue("Centered text must use its visible width, not the single-line scrolling width",
                                requireNotNull(control.layout).width <= control.width)
                        }
                    }
                    File(context.cacheDir, "play-$stage-$widthDp-bounds.txt").writeText(controls.joinToString("\n") {
                        "${context.resources.getResourceEntryName(it.id)}: ${it.left},${it.top},${it.width},${it.height} " +
                            if (it is android.widget.TextView) "text=${it.text} layoutWidth=${it.layout?.width}" else ""
                    })
                    val bitmap = Bitmap.createBitmap(binding.root.width, binding.root.height, Bitmap.Config.ARGB_8888)
                    try {
                        binding.root.draw(Canvas(bitmap))
                        File(context.cacheDir, "play-$stage-$widthDp.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }
}
