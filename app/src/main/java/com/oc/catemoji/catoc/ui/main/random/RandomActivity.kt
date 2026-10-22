package com.oc.catemoji.catoc.ui.main.random

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.select
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.databinding.ActivityRandomBinding
import com.oc.catemoji.catoc.ui.main.cosplay.CosplayViewModel
import com.oc.catemoji.catoc.ui.main.customize.CustomizeActivity
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.forEach

@AndroidEntryPoint
class RandomActivity : BaseActivity<ActivityRandomBinding, RandomViewModel>(
    ActivityRandomBinding::inflate,
    RandomViewModel::class.java
) {
private fun initNativeCollab() {
        Log.d(
            "NativeCollab",
            "🔄 initNativeCollab called, stack: ${Thread.currentThread().stackTrace[3]}"
        )
        Admob.getInstance().loadNativeCollapNotBanner(
            this@RandomActivity,
            getString(R.string.native_cl_randon),
            binding.flNativeCollab
        )
    }

    override fun onScreenStart() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        initNativeCollab()
    }

    override fun onScreenStop() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }

    override fun initView() {
        initNativeCollab()
        binding.setupActionBar()
    }

    private fun ActivityRandomBinding.setupActionBar() {
        actionBar.apply {
            txtShow.select()
            tvEdit.select()
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.random))
        }
    }

    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarLeft.onClick {
                    popBack()
            }
            random.onClick {
                val isOnline =
                    isNetworkConnected(requireContext()) && isInternetAvailable(requireContext())
                if (!isOnline) {
                    showUnstableNetworkDialog()
                    return@onClick
                }
                showInter {
                    viewModel.randomize(isOnline = isOnline)
                }
            }
            btnEdit.onClick {
                val item = viewModel.randomItem.value ?: return@onClick
                val isOnline =
                    isNetworkConnected(requireContext()) && isInternetAvailable(requireContext())
                val templateId =
                    viewModelActivity.templates.value.getOrNull(item.templateIndex)?.id ?: ""
                if (templateId.startsWith("online_") && !isOnline) {
                    showUnstableNetworkDialog()
                    return@onClick
                }
                val args = CustomizeActivity.newArgs(
                    templateIndex = item.templateIndex,
                    templateId = item.template.id,
                    isEdit = false,
                    savedSelections = item.selections
                )
                activityRouter().navigate(R.id.action_random_to_custom, args)
            }
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isDataReady.collect { ready ->
                if (ready && viewModel.randomItem.value == null) {
                    val isOnline =
                        isNetworkConnected(requireContext()) && isInternetAvailable(requireContext())
                    viewModel.randomize(isOnline = isOnline)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.randomItem.collectLatest { item ->
                item ?: return@collectLatest

                // ✅ Nếu đã có cache bitmap thì không render lại
                val cached = viewModel.cachedBitmap
                if (cached != null && !cached.isRecycled) {
                    showBitmap(cached)
                    return@collectLatest
                }

                renderCharacter(item)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ✅ Khi back về: dùng lại bitmap đã cache
        val cached = viewModel.cachedBitmap
        if (cached != null && !cached.isRecycled) {
            showBitmap(cached)
        }
    }

    private suspend fun renderCharacter(item: RandomViewModel.RandomItem) {
        val imageContext = requireContext().applicationContext
        binding.imvImage.setImageDrawable(null)
        try {
            val paths = item.resolvedPaths.filterNotNull()
            if (paths.isEmpty()) return
            showLoadingSafe()

            val bitmaps = withContext(Dispatchers.IO) {
                paths.map { path ->
                    async {
                        runCatching {
                            Glide.with(imageContext)
                                .asBitmap()
                                .load(path)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(800)
                                .submit()
                                .get()
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }

            if (bitmaps.isEmpty()) return

            val merged = mergeBitmaps(bitmaps)

            // ✅ Lưu vào cache
            viewModel.setCachedBitmap(merged)

            withContext(Dispatchers.Main) {
                showBitmap(merged)
            }
        } finally {
            hideLoadingSafe()
        }
    }

    private fun showBitmap(bitmap: Bitmap) {
        binding.imvImage.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }
    }

    private fun mergeBitmaps(bitmaps: List<Bitmap>): Bitmap {
        val size = 800
        val merged = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(merged)
        bitmaps.forEach { bmp ->
            val scaled = if (bmp.width == size && bmp.height == size) bmp
            else Bitmap.createScaledBitmap(bmp, size, size, true)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            if (scaled != bmp) scaled.recycle()
        }
        return merged
    }

    override fun bindViewModel() {}
}
