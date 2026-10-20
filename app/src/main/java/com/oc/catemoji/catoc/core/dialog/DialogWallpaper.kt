package com.oc.catemoji.catoc.core.dialog

import android.app.WallpaperManager
import android.content.Context
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseDialog
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.DialogWallpaperBinding

class DialogWallpaper(context: Context) :
    BaseDialog<DialogWallpaperBinding>(context, maxWidth = true) {

    override val layoutId = R.layout.dialog_wallpaper
    override val isCancelOnTouchOutside = false
    override val isCancelableByBack = true

    var onSetWallpaper: (Int) -> Unit = {}
    private var selectedFlags = WallpaperManager.FLAG_LOCK
    private var isSubmitted = false

    override fun initView() {
        binding.tvTitle.isSelected = true
        binding.tvCancel.isSelected = true
        binding.tvDone.isSelected = true
        selectTarget(selectedFlags)
    }


    override fun initAction() {
        binding.apply {
            lock.onClick { selectTarget(WallpaperManager.FLAG_LOCK) }
            tvLock.onClick { selectTarget(WallpaperManager.FLAG_LOCK) }
            home.onClick { selectTarget(WallpaperManager.FLAG_SYSTEM) }
            tvHome.onClick { selectTarget(WallpaperManager.FLAG_SYSTEM) }
            lockAndHome.onClick { selectTarget(WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM) }
            tvLockAndHome.onClick { selectTarget(WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM) }
            btnCancel.onClick { dismiss() }
            btnDone.onClick { applyWallpaper(selectedFlags) }
        }
    }

    private fun applyWallpaper(flags: Int) {
        if (isSubmitted) return
        isSubmitted = true
        selectTarget(flags)
        dismiss()
        onSetWallpaper(flags)
    }

    private fun selectTarget(flags: Int) {
        selectedFlags = flags
        listOf(
            binding.lock to WallpaperManager.FLAG_LOCK,
            binding.home to WallpaperManager.FLAG_SYSTEM,
            binding.lockAndHome to (WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM)
        ).forEach { (icon, target) ->
            icon.isSelected = flags == target
            icon.setImageResource(
                if (icon.isSelected) R.drawable.ic_select_wall else R.drawable.ic_unselect_wall
            )
        }
    }

    override fun onDismissListener() = Unit
}
