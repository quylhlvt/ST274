package com.oc.catemoji.catoc.ui.main.done

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setFrameActionBar
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.databinding.ActivityDoneBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeActivity
import com.oc.catemoji.catoc.ui.main.pause.PauseOCChallengeActivity
import com.oc.catemoji.catoc.ui.main.successchallenge.SuccessChallengeActivity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class DoneActivity : BaseActivity<ActivityDoneBinding, ViewModel>(
    ActivityDoneBinding::inflate,
    ViewModel::class.java
) {
    private var restarting = false
    private fun initNativeCollab() {
        Admob.getInstance()
            .loadNativeCollapNotBanner(
                this@DoneActivity,
                getString(R.string.native_cl_playPauseDone),
                binding.flNativeCollab
            )
    }

    override fun onRestart() {
        super.onRestart()
        initNativeCollab()
    }
    override fun initView() {
        initNativeCollab()
        setImageActionBar(binding.actionBar.btnActionBarLeft, R.drawable.back_app)
        setFrameActionBar(binding.actionBar.btnActionBarRightText,binding.actionBar.tvRightText,getString(R.string.next ))
        binding.txtPlayoragain.isSelected = true

        val image = intent.getStringExtra(EXTRA_IMAGE_PATH)?.let(::File)
        if (image == null || !image.isFile) {
            showToast(R.string.errorcontent)
            finish()
            return
        }

        Glide.with(this).load(image).into(binding.imgResult)
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { goBack() }
        binding.actionBar.btnActionBarRightText.onClick {
            val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH) ?: return@onClick
            startActivity(Intent(this, SuccessChallengeActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_PATH, imagePath)
            })
        }
        binding.btnTryAgain.onClick {
            restartChallenge()
        }
    }
    // Back: quay về Pause cũ, giữ nguyên ảnh đã chọn. Không tạo session mới.
    private fun goBack() {
        finish()
    }
    private fun restartChallenge() {
        if (restarting) return
        restarting = true
        showInter {
            // Tạo Activity mới để bắt đầu một challenge session hoàn toàn mới.
            startActivity(Intent(this@DoneActivity, PauseOCChallengeActivity::class.java).apply {
                putExtra(
                    CustomizeActivity.ARG_TEMPLATE_ID,
                    intent.getStringExtra(CustomizeActivity.ARG_TEMPLATE_ID)
                )
                putExtra(
                    CustomizeActivity.ARG_TEMPLATE_INDEX,
                    intent.getIntExtra(CustomizeActivity.ARG_TEMPLATE_INDEX, 0)
                )
                putExtra(PauseOCChallengeActivity.EXTRA_RESTART_FROM_DONE, true)
            })
            finish()
        }
    }

    override fun onScreenBackPressed(): Boolean {
        goBack()
        return true
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"
    }
}
