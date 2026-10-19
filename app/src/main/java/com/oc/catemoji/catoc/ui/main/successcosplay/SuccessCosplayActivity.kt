package com.oc.catemoji.catoc.ui.main.successcosplay

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.loadNativeCollabAds
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.select
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.databinding.ActivitySuccessCosplayBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SuccessCosplayActivity : BaseActivity<ActivitySuccessCosplayBinding, SuccessCosplayViewModel>(
    ActivitySuccessCosplayBinding::inflate,
    SuccessCosplayViewModel::class.java
) {
override fun initView() {
        loadNativeCollabAds(getString(R.string.native_cl_cosplaySuccess), binding.flNativeCollab)
        binding.apply {
            txtShow.isSelected = true
            actionBar.apply {
                tvCenter.select()
                setImageActionBar(btnActionBarRight, R.drawable.ic_home)
                setTextActionBar(tvCenter, getString(R.string.successfully))
            }
            viewModelActivity.userResultBitmap?.takeIf { !it.isRecycled }?.let(imvImage2::setImageBitmap)
            view33.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                root.doOnPreDraw { alignProgressMarkers() }
            }
            val percent = viewModelActivity.cosplayPercent.coerceIn(0, 100)
            tvMatchPercent.text = "$percent/100"
            view33.updateLayoutParams<ConstraintLayout.LayoutParams> { horizontalBias = percent / 100f }
            root.doOnPreDraw { alignProgressMarkers() }
        }
    }

    private fun alignProgressMarkers() {
        val b = binding
        val bounds = Rect()
        b.layoutProgress.offsetDescendantRectToMyCoords(
            b.view33, bounds.apply { set(0, 0, b.view33.width, b.view33.height) }
        )
        val centerX = bounds.exactCenterX()
        b.imgStar.x = centerX - b.imgStar.width / 2f
        b.imgStar.y = bounds.exactCenterY() - b.imgStar.height / 2f
        b.tvMatchPercent.x = b.layoutProgress.x + centerX - b.tvMatchPercent.width / 2f
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarRight.onClick {
            showInter {
                val homeIntent = android.content.Intent(
                    this,
                    com.oc.catemoji.catoc.ui.main.home.HomeActivity::class.java
                ).addFlags(
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                startActivity(homeIntent)
                finish()
            }
        }
        binding.btnTryAgain.onClick {
            showInter {
                viewModelActivity.shouldRestartShow = true
                finish()
            }
        }
    }

    override fun bindViewModel() = Unit
    override fun observeData() = Unit

}
