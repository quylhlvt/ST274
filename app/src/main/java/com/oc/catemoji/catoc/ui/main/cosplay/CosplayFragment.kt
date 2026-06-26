package com.oc.catemoji.catoc.ui.main.cosplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.popBack
import com.oc.catemoji.catoc.core.extention.select
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.databinding.FragmentCosplayBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeFragment
import com.oc.catemoji.catoc.ui.main.random.RandomViewModel
import com.oc.catemoji.catoc.ui.main.show.ShowFragment
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CosplayFragment : BaseFragment<FragmentCosplayBinding, CosplayViewModel>(
    FragmentCosplayBinding::inflate,
    CosplayViewModel::class.java
) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackPressHandler()
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    popBack()
                }
            }
        )
    }

    private fun initNativeCollab() {
        Log.d(
            "NativeCollab",
            "🔄 initNativeCollab called, stack: ${Thread.currentThread().stackTrace[3]}"
        )
        Admob.getInstance().loadNativeCollapNotBanner(
            requireContext(),
            getString(R.string.native_cl_cosplayCustom),
            binding.flNativeCollab
        )
    }

    override fun onFragmentStart() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        initNativeCollab()
    }

    override fun onFragmentStop() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }

    override fun initView() {
        initNativeCollab()
        binding.setupActionBar()

        // Chỉ randomize lần đầu, nếu chưa có item nào
//        if (viewModel.randomItem.value == null) {
//            viewModel.randomize()
//        }
    }

    private fun FragmentCosplayBinding.setupActionBar() {
        actionBar.apply {
            tvCenter.select()
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setImageActionBar(btnActionBarRight, R.drawable.guid)
            setTextActionBar(tvCenter, getString(R.string.cosplay))
        }
    }

    override fun viewListener() {
        binding.apply {

            actionBar.btnActionBarLeft.onClick {
                showInter {
                    popBack()
                }
            }

            random.onClick {
                val isOnline =
                    isNetworkConnected(requireContext()) && isInternetAvailable(requireContext())

                if (!isOnline) {
                    showUnstableNetworkDialog()
                    return@onClick
                }

                viewModel.randomize(isOnline = isOnline)
            }
            actionBar.btnActionBarRight.onClick {
                showGuide.visible()
            }
            closeGuide.onClick {
                showGuide.gone()
            }
            show.onClick {
                val item = viewModel.randomItem.value ?: return@onClick

                val isOnline =
                    isNetworkConnected(requireContext()) && isInternetAvailable(requireContext())
                val templateId =
                    viewModelActivity.templates.value.getOrNull(item.templateIndex)?.id ?: ""
                if (templateId.startsWith("online_") && !isOnline) {
                    showUnstableNetworkDialog()
                    return@onClick
                }

                val cached = viewModel.cachedBitmap
                if (cached != null && !cached.isRecycled) {
                    viewModelActivity.cosplayBitmap = cached
                }
                val args = ShowFragment.newArgshow(
                    templateIndex = item.templateIndex,
                    targetSelections = item.selections
                )
                findNavController().navigate(R.id.action_cosplay_to_show, args)
            }
        }
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentCosplayBinding = FragmentCosplayBinding.inflate(inflater, container, false)

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

                // Nếu đã có cache bitmap thì không render lại
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
        // ✅ Guard: chỉ access viewModel khi fragment đã attach xong
        if (!isAdded || view == null) return

        val cached = viewModel.cachedBitmap
        if (cached != null && !cached.isRecycled) {
            showBitmap(cached)
        }
    }

    private fun renderCharacter(item: CosplayViewModel.RandomItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val paths = item.resolvedPaths.filterNotNull()
            if (paths.isEmpty()) return@launch
            binding.imvImage.setImageDrawable(null)

            showLoadingSafe()

            val bitmaps = withContext(Dispatchers.IO) {
                paths.map { path ->
                    async {
                        runCatching {
                            Glide.with(requireContext())
                                .asBitmap()
                                .load(path)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .override(512)
                                .submit()
                                .get()
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }

            hideLoadingSafe()
            if (bitmaps.isEmpty()) return@launch

            val merged = mergeBitmaps(bitmaps)

            // ✅ Lưu vào cache
            viewModel.setCachedBitmap(merged)

            withContext(Dispatchers.Main) {
                showBitmap(merged)
            }
        }
    }

    private fun showBitmap(bitmap: Bitmap) {
        binding.imvImage.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }
    }

    private fun mergeBitmaps(bitmaps: List<Bitmap>): Bitmap {
        val size = 512
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