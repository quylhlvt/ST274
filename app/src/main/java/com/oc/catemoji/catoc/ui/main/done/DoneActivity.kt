package com.oc.catemoji.catoc.ui.main.done

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.databinding.ActivityDoneBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeActivity
import com.oc.catemoji.catoc.ui.main.pause.PauseOCChallengeActivity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class DoneActivity : BaseActivity<ActivityDoneBinding, ViewModel>(
    ActivityDoneBinding::inflate,
    ViewModel::class.java
) {
    private var restarting = false

    override fun initView() {
        setImageActionBar(binding.actionBar.btnActionBarLeft, R.drawable.back_app)
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
        binding.actionBar.btnActionBarLeft.onClick { finish() }
        binding.btnTryAgain.onClick {
            if (restarting) return@onClick
            restarting = true
            // A new Activity gets a fresh challenge session for the same template.
            startActivity(Intent(this, PauseOCChallengeActivity::class.java).apply {
                putExtra(
                    CustomizeActivity.ARG_TEMPLATE_ID,
                    intent.getStringExtra(CustomizeActivity.ARG_TEMPLATE_ID)
                )
                putExtra(
                    CustomizeActivity.ARG_TEMPLATE_INDEX,
                    intent.getIntExtra(CustomizeActivity.ARG_TEMPLATE_INDEX, 0)
                )
            })
            finish()
        }
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"
    }
}
