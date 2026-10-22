package com.oc.catemoji.catoc.ui.main.preview

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.databinding.ActivityPreViewBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class PreViewActivity : BaseActivity<ActivityPreViewBinding, ViewModel>(
    ActivityPreViewBinding::inflate,
    ViewModel::class.java
) {
    private val imagePath by lazy { intent.getStringExtra(EXTRA_IMAGE_PATH).orEmpty() }
    private fun initNativeCollab() {
        Admob.getInstance()
            .loadNativeCollapNotBanner(
                this@PreViewActivity,
                getString(R.string.native_cl_playDIY_view),
                binding.flNativeCollab
            )
    }

    override fun onRestart() {
        super.onRestart()
        initNativeCollab()
    }
    override fun initView() {
        initNativeCollab()
        val imageFile = File(imagePath)
        if (imagePath.isBlank() || !imageFile.isFile) {
            showToast(R.string.errorcontent)
            finish()
            return
        }

        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
        }
        Glide.with(this)
            .load(imageFile)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(binding.drawView)
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { finish() }
    }

    override fun onScreenBackPressed(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"

        fun newIntent(context: Context, imagePath: String): Intent =
            Intent(context, PreViewActivity::class.java)
                .putExtra(EXTRA_IMAGE_PATH, imagePath)
    }
}
