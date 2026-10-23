package com.oc.catemoji.catoc.ui.main.viewMyDiy

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.dialog.DialogWallpaper
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.helper.WallpaperHelper
import com.oc.catemoji.catoc.databinding.ActivityViewMyDiyactivityBinding
import com.oc.catemoji.catoc.ui.main.view.ViewViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class ViewMyDIYActivity : BaseActivity<ActivityViewMyDiyactivityBinding, ViewViewModel>(
    ActivityViewMyDiyactivityBinding::inflate,
    ViewViewModel::class.java
) {
    private val imagePath: String by lazy { intent.getStringExtra(EXTRA_IMAGE_PATH).orEmpty() }
    private var wallpaperDialog: DialogWallpaper? = null
    private var isSettingWallpaper = false
    private fun initNativeCollab() {
        Admob.getInstance()
            .loadNativeCollapNotBanner(
                this@ViewMyDIYActivity,
                getString(R.string.native_cl_detail),
                binding.flNativeCollab
            )
    }

    override fun onRestart() {
        super.onRestart()
        initNativeCollab()
    }
    override fun initView() {
        initNativeCollab()
        binding.apply {
            setImageActionBar(actionBar.btnActionBarLeft, R.drawable.back_app)

            setImageActionBar(actionBar.btnActionBarNextToRight, R.drawable.ic_share)
            setImageActionBar(actionBar.btnActionBarRight, R.drawable.ic_delete)
            txtWall.setText(R.string.set_wallpaper2)
            txtWall.isSelected = true
        }
        val file = currentImageFile() ?: return
        loadImage(this, file.absolutePath, binding.imgWallDIY)
    }

    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarLeft.onClick { finish() }
            actionBar.btnActionBarNextToRight.onClick { shareImage() }
            actionBar.btnActionBarRight.onClick { confirmDelete() }
            btnBottomWall.onClick { showWallpaperDialog() }
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

    private fun confirmDelete() {
        val file = currentImageFile() ?: return
        showConfirmDialog(
            title = getString(R.string.delete),
            message = getString(R.string.are_you_sure_want_to_delete_this_item),
            onYes = {
                viewModel.deleteFile(file.absolutePath, isAvatar = false) { finish() }
            }
        )
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
        binding.btnBottomWall.isEnabled = false
        lifecycleScope.launch {
            try {
                showToast(WallpaperHelper.setWallpaper(applicationContext, path, flags))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ViewMyDIYActivity", "Unable to set DIY wallpaper", e)
                showToast(R.string.wallpaper_set_failed)
            } finally {
                isSettingWallpaper = false
                binding.btnBottomWall.isEnabled = true
            }
        }
    }

    override fun onScreenStop() {
        wallpaperDialog?.dismiss()
    }

    companion object {
        private const val EXTRA_IMAGE_PATH = "imagePath"

        fun newIntent(context: Context, imagePath: String): Intent =
            Intent(context, ViewMyDIYActivity::class.java).putExtra(EXTRA_IMAGE_PATH, imagePath)
    }
}
