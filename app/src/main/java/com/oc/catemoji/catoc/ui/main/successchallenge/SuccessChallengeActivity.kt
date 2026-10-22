package com.oc.catemoji.catoc.ui.main.successchallenge

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.onClick2
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.helper.DownloadHelper
import com.oc.catemoji.catoc.databinding.ActivitySuccessChallengeBinding
import com.oc.catemoji.catoc.ui.main.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class SuccessChallengeActivity : BaseActivity<ActivitySuccessChallengeBinding, ViewModel>(
    ActivitySuccessChallengeBinding::inflate,
    ViewModel::class.java
) {
    private val imagePath by lazy { intent.getStringExtra(EXTRA_IMAGE_PATH).orEmpty() }
    private var isDownloading = false
    private fun initNativeCollab() {
        Admob.getInstance()
            .loadNativeCollapNotBanner(
                this@SuccessChallengeActivity,
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
        val imageFile = currentImageFile() ?: return
        setImageActionBar(binding.actionBar.btnActionBarLeft, R.drawable.back_app)
        setTextActionBar(binding.actionBar.tvCenter, getString(R.string.successful))
        setImageActionBar(binding.actionBar.btnActionBarNextToRight, R.drawable.ic_share)
        setImageActionBar(binding.actionBar.btnActionBarRight, R.drawable.ic_home)
        Glide.with(this).load(imageFile).into(binding.imvImage)
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { showInter {
            finish()
        }}
        binding.btnBottomRight.onClick { downloadImage() }
        binding.actionBar.btnActionBarNextToRight.onClick2 {
            shareImage()
        }
        binding.actionBar.btnActionBarRight.onClick2 {
          showInter {
              home()
          }
        }
    }

    private fun currentImageFile(): File? {
        val file = File(imagePath)
        if (imagePath.isBlank() || !file.isFile) {
            showToast(R.string.errorcontent)
            finish()
            return null
        }
        return file
    }

    private fun downloadImage() {
        if (isDownloading) return
        val file = currentImageFile() ?: return
        isDownloading = true
        binding.btnBottomRight.isEnabled = false
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                DownloadHelper.downloadToGallery(
                    this@SuccessChallengeActivity,
                    file.absolutePath
                )
            }
            isDownloading = false
            binding.btnBottomRight.isEnabled = true
            showToast(
                if (success) getString(R.string.download_success, getString(R.string.app_name))
                else getString(R.string.download_failed_please_try_again_later)
            )
        }
    }

    private fun shareImage() {
        val file = currentImageFile() ?: return

        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("challenge_image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        } catch (_: ActivityNotFoundException) {
            showToast(R.string.no_app_found_to_handle_this_action)
        }
    }

    private fun home() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"
    }
}
