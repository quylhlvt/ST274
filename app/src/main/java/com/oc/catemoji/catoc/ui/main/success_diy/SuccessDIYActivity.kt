package com.oc.catemoji.catoc.ui.main.success_diy

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.TaskStackBuilder
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.dialog.DialogWallpaper
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.onClick2
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.helper.DownloadHelper
import com.oc.catemoji.catoc.core.helper.WallpaperHelper
import com.oc.catemoji.catoc.databinding.ActivitySuccessDiyactivityBinding
import com.oc.catemoji.catoc.ui.main.home.HomeActivity
import com.oc.catemoji.catoc.ui.main.myPony.MyCreationTab
import com.oc.catemoji.catoc.ui.main.myPony.MyPonyActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class SuccessDIYActivity : BaseActivity<ActivitySuccessDiyactivityBinding, ViewModel>(
    ActivitySuccessDiyactivityBinding::inflate,
    ViewModel::class.java
) {
    private val imagePath by lazy { intent.getStringExtra(EXTRA_IMAGE_PATH).orEmpty() }
    private var wallpaperDialog: DialogWallpaper? = null
    private var isDownloading = false
    private var isSettingWallpaper = false
    private fun initNativeCollab() {
        Admob.getInstance()
            .loadNativeCollapNotBanner(
                this@SuccessDIYActivity,
                getString(R.string.native_cl_playDIY_done),
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
        binding.apply {
            txtLeft.isSelected = true
            txtRight.isSelected = true
            txtWall.isSelected = true
            actionBar.apply {
                setImageActionBar(btnActionBarLeft, R.drawable.back_app)
                setTextActionBar(tvCenter, getString(R.string.successful))
                setImageActionBar(btnActionBarNextToRight, R.drawable.ic_share)
                setImageActionBar(btnActionBarRight, R.drawable.ic_home)
            }
        }
        Glide.with(this)
            .load(imageFile)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(binding.imvImage)
    }

    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarLeft.onClick { finish() }
            actionBar.btnActionBarNextToRight.onClick2 { shareImage() }
            actionBar.btnActionBarRight.onClick2 {
                showInter {
                    openHome()
                }
            }
            btnBottomLeft.onClick2 {
                showInter {
                    openMyDiy()
                }
            }
            btnBottomRight.onClick2 { downloadImage() }
            btnWall.onClick { showWallpaperDialog() }
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

    private fun shareImage() {
        val file = currentImageFile() ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("DIY", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        } catch (_: ActivityNotFoundException) {
            showToast(R.string.no_app_found_to_handle_this_action)
        }
    }

    private fun downloadImage() {
        if (isDownloading) return
        val file = currentImageFile() ?: return
        isDownloading = true
        binding.btnBottomRight.isEnabled = false
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                DownloadHelper.downloadToGallery(
                    this@SuccessDIYActivity,
                    file.absolutePath
                )
            }
            isDownloading = false
            binding.btnBottomRight.isEnabled = true
            showToast(
                if (success) {
                    getString(R.string.download_success, getString(R.string.app_name))
                } else {
                    getString(R.string.download_failed_please_try_again_later)
                }
            )
        }
    }

    private fun showWallpaperDialog() {
        if (isSettingWallpaper || wallpaperDialog?.isShowing == true) return
        val file = currentImageFile() ?: return
        wallpaperDialog = DialogWallpaper(this).apply {
            onSetWallpaper = { flags -> setWallpaper(file.absolutePath, flags) }
            setOnDismissListener { wallpaperDialog = null }
            show()
        }
    }

    private fun setWallpaper(path: String, flags: Int) {
        if (isSettingWallpaper) return
        isSettingWallpaper = true
        binding.btnWall.isEnabled = false
        lifecycleScope.launch {
            try {
                showToast(WallpaperHelper.setWallpaper(applicationContext, path, flags))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("SuccessDIYActivity", "Unable to set DIY wallpaper", error)
                showToast(R.string.wallpaper_set_failed)
            } finally {
                isSettingWallpaper = false
                binding.btnWall.isEnabled = true
            }
        }
    }

    private fun openHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun openMyDiy() {
        val home = Intent(this, HomeActivity::class.java)
        val creations = Intent(this, MyPonyActivity::class.java).apply {
            putExtra(MyPonyActivity.EXTRA_INITIAL_TAB, MyCreationTab.DIY.name)
        }
        TaskStackBuilder.create(this)
            .addNextIntent(home)
            .addNextIntent(creations)
            .startActivities()
        finish()
    }

    override fun onScreenStop() {
        wallpaperDialog?.dismiss()
        wallpaperDialog = null
    }

    override fun onScreenBackPressed(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"

        fun newIntent(context: Context, imagePath: String): Intent =
            Intent(context, SuccessDIYActivity::class.java)
                .putExtra(EXTRA_IMAGE_PATH, imagePath)
    }
}
