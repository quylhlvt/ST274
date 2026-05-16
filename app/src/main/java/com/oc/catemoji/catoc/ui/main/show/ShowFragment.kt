package com.oc.catemoji.catoc.ui.main.show

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.popBack
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import com.oc.catemoji.catoc.databinding.FragmentShowBinding
import com.oc.catemoji.catoc.ui.main.customize.ColorAdapter
import com.oc.catemoji.catoc.ui.main.customize.NavAdapter
import com.oc.catemoji.catoc.ui.main.customize.PartAdapter
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger

@AndroidEntryPoint
class ShowFragment : BaseFragment<FragmentShowBinding, ShowViewModel>(
    FragmentShowBinding::inflate,
    ShowViewModel::class.java
) {
    // ── Layer views (giống CustomizeFragment) ─────────────────────────────────
    private val layerViews = arrayListOf<AppCompatImageView>()
    private val navToLayerIndex = mutableMapOf<String, Int>()
    private val arrShowColor = mutableListOf<Boolean>()

    private val adapterNav by lazy { NavAdapter() }
    private val adapterColor by lazy { ColorAdapter() }
    private val adapterPart by lazy { PartAdapter() }

    private val pendingLoads = AtomicInteger(0)
    private var timerJob: Job? = null
    private val totalSeconds = 5 * 60
    // ── INFLATE ───────────────────────────────────────────────────────────────

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentShowBinding = FragmentShowBinding.inflate(inflater, container, false)
    private fun isOnlineTemplate(): Boolean {
        val templateIndex = arguments?.getInt(ARG_TEMPLATE_INDEX, 0) ?: 0
        return viewModelActivity.templates.value
            .getOrNull(templateIndex)?.id?.startsWith("online_") == true
    }

    private fun checkOnlineNetworkOrShowDialog(): Boolean {
        if (!isOnlineTemplate()) return false
        return when {
            !isInternetAvailable(requireContext()) -> {
                showUnstableNetworkDialog(); true
            }
            !isNetworkConnected(requireContext()) -> {
                showUnstableNetworkDialog(); true
            }
            else -> false
        }
    }
    // ── INIT ──────────────────────────────────────────────────────────────────
    private fun initNativeCollab() {
        Log.d(
            "NativeCollab",
            "🔄 initNativeCollab called, stack: ${Thread.currentThread().stackTrace[3]}"
        )
        Admob.getInstance().loadNativeCollapNotBanner(
            requireContext(),
            getString(R.string.native_cl_cosplaySuccess),
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
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setImageActionBar(btnActionBarRight, R.drawable.next_app)
            setTextActionBar( tvCenter, "05:00" )
        }
        setupAdapters()
        readArgsAndInit()
        startCountDown()
        val bitmap = viewModelActivity.cosplayBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            binding.imvImage2.setImageBitmap(bitmap)
            binding.imvImage.setImageBitmap(bitmap)
            binding.imvImage2.visibility = View.VISIBLE
        } else {
            binding.imvImage2.visibility = View.GONE
        }
    }
    private fun startTimer() {
        timerJob?.cancel()
        var remainingSeconds = totalSeconds
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (remainingSeconds > 0) {
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                binding.actionBar.tvCenter.text =
                    String.format("%02d:%02d", minutes, seconds)
                kotlinx.coroutines.delay(1000)
                remainingSeconds--
            }
            // Hết giờ → navigate
            binding.actionBar.tvCenter.text = "00:00"
            if (isAdded && !isDetached) {
                navigateToSuccess()
            }
        }
    }
    private fun startCountDown() {
        binding.actionBar.btnActionBarLeft.isEnabled = false
        binding.actionBar.btnActionBarRight.isEnabled = false
        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.app_color5), // 3
            ContextCompat.getColor(requireContext(), R.color.app_color6), // 2
            ContextCompat.getColor(requireContext(), R.color.orange_F6)   // 1
        )

        binding.countDown.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            for (count in 3 downTo 1) {
                val colorIndex = 3 - count
                binding.tvCountDown.setTextColor(colors[colorIndex])
                binding.tvCountDown.text = count.toString()

                // reset state trước khi animate in
                binding.tvCountDown.scaleX = 0.4f
                binding.tvCountDown.scaleY = 0.4f
                binding.tvCountDown.alpha = 0f

                // pop in — chờ xong
                suspendCancellableCoroutine { cont ->
                    binding.tvCountDown.animate()
                        .scaleX(1f).scaleY(1f)
                        .alpha(1f)
                        .setDuration(350)
                        .withEndAction { cont.resume(Unit) {} }
                        .start()
                }

                // giữ 500ms
                kotlinx.coroutines.delay(500)

                // pop out — chờ xong
                suspendCancellableCoroutine { cont ->
                    binding.tvCountDown.animate()
                        .scaleX(1.5f).scaleY(1.5f)
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction { cont.resume(Unit) {} }
                        .start()
                }
            }

            // Ẩn overlay, bắt đầu timer
            binding.countDown.visibility = View.GONE
            binding.actionBar.btnActionBarLeft.isEnabled = true
            binding.actionBar.btnActionBarRight.isEnabled = true
            binding.tvCountDown.alpha = 1f
            binding.tvCountDown.scaleX = 1f
            binding.tvCountDown.scaleY = 1f
            startTimer()
        }
    }
    private fun readArgsAndInit() {
        val templateIndex = arguments?.getInt(ARG_TEMPLATE_INDEX, 0) ?: 0
        val targetSelections: ArrayList<SelectionIndex> =
            arguments?.getParcelableArrayList(ARG_SELECTIONS) ?: return

        viewModel.init(templateIndex, targetSelections)
    }

    private fun setupAdapters() {
        binding.rcvNav.adapter = adapterNav
        binding.rcvColor.adapter = adapterColor
        binding.rcvPart.adapter = adapterPart
    }
    private fun navigateToSuccess() {
        timerJob?.cancel()
        if (!isAdded || isDetached) return

        // Nếu vẫn còn đang load ảnh → đợi
        if (pendingLoads.get() > 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                while (pendingLoads.get() > 0) {
                    kotlinx.coroutines.delay(50)
                }
                doNavigateToSuccess()
            }
        } else {
            doNavigateToSuccess()
        }
    }

    private fun doNavigateToSuccess() {
        if (!isAdded || isDetached) return
        val bitmap = renderLayersToBitmap()
        if (bitmap != null) viewModelActivity.userResultBitmap = bitmap
        viewModelActivity.cosplayPercent = viewModel.state.value.matchPercent
        findNavController().navigate(R.id.action_show_to_successCosplay)
    }
    private fun renderLayersToBitmap(): Bitmap? {
        val root = binding.rlCharacter
        if (root.width == 0 || root.height == 0) return null
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layerViews.forEach { iv ->
            if (iv.visibility != View.VISIBLE) return@forEach
            val drawable = iv.drawable ?: return@forEach
            canvas.save()
            if (iv.scaleX < 0) canvas.scale(-1f, 1f, root.width / 2f, 0f)
            drawable.setBounds(0, 0, root.width, root.height)
            drawable.draw(canvas)
            canvas.restore()
        }
        return bitmap
    }

    // ── LISTENERS ─────────────────────────────────────────────────────────────

    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarLeft.onClick {showInter {
                popBack()
            }  }
            actionBar.btnActionBarRight.onClick { showInter {
                navigateToSuccess()
            }}

            materialSmall.onClick { imgShowBig.visible() }
            close.onClick { imgShowBig.gone() }

            end.onClick {
                val navPos = viewModel.state.value.currentNavIndex
                if (navPos < arrShowColor.size) arrShowColor[navPos] = false
                llColor.animate().alpha(0f).setDuration(200).withEndAction {
                    llColor.visibility = View.INVISIBLE
                }.start()
            }

            // ── Random ────────────────────────────────────────────────────────────
            imgRandom.onClick {
                if (!checkOnlineNetworkOrShowDialog()) viewModel.randomizeAll() // ← guard
            }

            // ── Color toggle ──────────────────────────────────────────────────────
            imgChangColor.onClick {
                if (checkOnlineNetworkOrShowDialog()) return@onClick              // ← guard
                val navPos = viewModel.state.value.currentNavIndex
                if (!viewModel.state.value.hasMultipleColors) return@onClick
                if (navPos < arrShowColor.size) arrShowColor[navPos] = true
                if (llColor.isVisible) return@onClick
                llColor.visibility = View.VISIBLE
                llColor.alpha = 0f
                llColor.animate().alpha(1f).setDuration(200).start()
            }
        }

        // ── Nav ───────────────────────────────────────────────────────────────────
        adapterNav.onClick = {
            if (!checkOnlineNetworkOrShowDialog()) viewModel.selectNav(it)       // ← guard
        }

        // ── Color ─────────────────────────────────────────────────────────────────
        adapterColor.onClick = {
            if (!checkOnlineNetworkOrShowDialog()) viewModel.selectColor(it)     // ← guard
        }

        // ── Part ──────────────────────────────────────────────────────────────────
        adapterPart.onClick = { idx, type ->
            if (!checkOnlineNetworkOrShowDialog()) {                              // ← guard
                when (type) {
                    "none" -> viewModel.selectNone()
                    "dice" -> viewModel.selectDiceCurrent()
                    else   -> viewModel.selectPath(idx)
                }
            }
        }
    }

    // ── OBSERVE ───────────────────────────────────────────────────────────────

    // ShowFragment.observeData() — THÊM guard này
    // ✅ FIX — thêm flag giống CustomizeFragment
    private var hasTriggeredReInit = false

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    if (state.listData.isEmpty()) {
                        if (!hasTriggeredReInit) {
                            hasTriggeredReInit = true
                            readArgsAndInit()
                        }
                        return@collectLatest
                    }
                    hasTriggeredReInit = false  // reset khi có data

                    if (layerViews.size != state.listData.size) {
                        buildLayerViews(state.listData)
                    }
                    renderLayers(state)
                    updateAdapters(state)
                    val scale = if (state.isFlipped) -1f else 1f
                    layerViews.forEach { it.scaleX = scale }
                    updateMatchUI(state.matchPercent)
                    if (state.matchPercent >= 100) {
                        timerJob?.cancel()
                        if (isAdded && !isDetached) {
                            navigateToSuccess()
                        }
                    }
                }
            }
        }
    }

    // ── BUILD LAYER VIEWS (giống CustomizeFragment.buildLayerViews) ───────────

    private fun buildLayerViews(parts: List<BodyPartModel>) {
        layerViews.clear()
        navToLayerIndex.clear()
        binding.rlCharacter.removeAllViews()

        parts.sortedBy { it.position }.forEachIndexed { layerIdx, bp ->
            val iv = AppCompatImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            binding.rlCharacter.addView(iv)
            layerViews.add(iv)
            navToLayerIndex[bp.nav] = layerIdx
        }
    }

    // ── RENDER LAYERS (giống CustomizeFragment.renderLayers) ─────────────────

    private fun renderLayers(state: ShowState) {
        val pathsToLoad = state.listData.mapIndexedNotNull { i, bp ->
            val path = viewModel.resolveUserPathAt(i)
            val layerIndex = navToLayerIndex[bp.nav] ?: return@mapIndexedNotNull null
            val view = layerViews.getOrNull(layerIndex) ?: return@mapIndexedNotNull null

            if (path == null) {
                if (view.visibility != View.GONE) {
                    view.visibility = View.GONE
                    view.tag = null
                    Glide.with(binding.rlCharacter).clear(view)
                }
                return@mapIndexedNotNull null
            }
            if (view.tag == path && view.visibility == View.VISIBLE) return@mapIndexedNotNull null
            Triple(view, path, layerIndex)
        }

        if (pathsToLoad.isEmpty()) {
            viewModel.onLoadingComplete()
            return
        }

        pendingLoads.set(pathsToLoad.size)

        pathsToLoad.forEach { (view, path, _) ->
            view.tag = path
            view.visibility = View.VISIBLE
            view.scaleX = if (viewModel.state.value.isFlipped) -1f else 1f
            loadImageIntoView(view, path)
        }
    }

    private fun loadImageIntoView(view: ImageView, path: String) {
        Glide.with(binding.rlCharacter)
            .load(path)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .priority(Priority.IMMEDIATE)
            .skipMemoryCache(false)
            .dontAnimate()
            .dontTransform()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?,
                    target: Target<Drawable>?, isFirstResource: Boolean
                ): Boolean {
                    onLoadFinished(); return false
                }

                override fun onResourceReady(
                    resource: Drawable?, model: Any?,
                    target: Target<Drawable>?, dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    onLoadFinished(); return false
                }
            })
            .into(view)
    }

    private fun onLoadFinished() {
        if (pendingLoads.decrementAndGet() <= 0) {
            pendingLoads.set(0)
            view?.post { viewModel.onLoadingComplete() }
        }
    }

    // ── ADAPTERS (giống CustomizeFragment.updateAdapters) ────────────────────

    private fun updateAdapters(state: ShowState) {
        adapterNav.setPos(state.currentNavIndex)
        adapterNav.submitList(state.listData)
        binding.imgChangColor.isVisible = state.hasMultipleColors

        adapterColor.setPos(state.currentColorIndex)

        // Khởi tạo arrShowColor
        if (arrShowColor.size != state.listData.size) {
            arrShowColor.clear()
            repeat(state.listData.size) { arrShowColor.add(true) }
        }

        val navPos = state.currentNavIndex

        if (state.hasMultipleColors) {
            binding.imgChangColor.visible()
            adapterColor.submitList(state.currentColors)
            binding.rcvColor.post {
                binding.rcvColor.smoothScrollToPosition(state.currentColorIndex)
            }
            if (navPos < arrShowColor.size && arrShowColor[navPos]) {
                binding.llColor.animate().alpha(1f).setDuration(150).withStartAction {
                    binding.llColor.visibility = View.VISIBLE
                }.start()
            } else {
                binding.imgChangColor.invisible()
                binding.llColor.animate().alpha(0f).setDuration(150).withEndAction {
                    binding.llColor.visibility = View.GONE
                }.start()
            }
        } else {
            binding.llColor.animate().alpha(0f).setDuration(150).withEndAction {
                binding.llColor.visibility = View.GONE
            }.start()
        }

        val bp = state.listData.getOrNull(state.currentNavIndex)
        val thumb = buildThumbList(bp, state.currentPaths)
        adapterPart.listThumb = thumb
        adapterPart.setPos(state.currentPathIndex)
        adapterPart.submitList(state.currentPaths)
        binding.rcvPart.post {
            binding.rcvPart.smoothScrollToPosition(state.currentPathIndex.coerceAtLeast(0))
        }
    }

    private fun buildThumbList(bp: BodyPartModel?, paths: List<String>): List<String> {
        val thumbs = bp?.listThumbPath ?: return paths
        if (thumbs.isEmpty()) return paths
        var idx = 0
        return paths.map { path ->
            when (path) {
                "none", "dice" -> path
                else -> thumbs.getOrElse(idx++) { path }
            }
        }
    }

    // ── PROGRESS (giống ShowActivity.updateMatchUI) ───────────────────────────

    private fun updateMatchUI(percent: Int) {
        binding.tvPercent.text = "$percent%"

        // Animate progress fill (scaleY từ 0→1 theo %)
        binding.progressTrack.post {
            val trackH = binding.progressTrack.height.toFloat()
            val marginPx = 5 * resources.displayMetrics.density
            val fillH = trackH - marginPx
            val scale = percent / 100f

            binding.progressFill.pivotX = binding.progressFill.width / 2f
            binding.progressFill.pivotY = fillH

            ObjectAnimator.ofFloat(
                binding.progressFill, "scaleY",
                binding.progressFill.scaleY,
                scale * fillH / trackH
            ).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }

            // Star icon chạy theo thanh progress
            val starH = binding.imgStar.height.toFloat()
            ObjectAnimator.ofFloat(
                binding.imgStar, "translationY",
                binding.imgStar.translationY,
                -(fillH * scale) - marginPx + starH / 2f
            ).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    // ── RESUME ────────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        pendingLoads.set(0)

        if (viewModelActivity.shouldRestartShow) {
            viewModelActivity.shouldRestartShow = false

            // Reset hoàn toàn
            timerJob?.cancel()
            viewModel.reset()
            hasTriggeredReInit = false

            // Clear tất cả layer
            layerViews.forEach { Glide.with(binding.rlCharacter).clear(it) }
            layerViews.clear()
            navToLayerIndex.clear()
            arrShowColor.clear()
            binding.rlCharacter.removeAllViews()

            // Cập nhật ảnh cosplay mới
            val bitmap = viewModelActivity.cosplayBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                binding.imvImage2.setImageBitmap(bitmap)
                binding.imvImage.setImageBitmap(bitmap)
                binding.imvImage2.visibility = View.VISIBLE
            }

            // Restart
            binding.actionBar.tvCenter.text = "05:00"
            startCountDown()
            readArgsAndInit()
            return
        }

        val currentState = viewModel.state.value
        if (currentState.listData.isEmpty()) {
            if (!hasTriggeredReInit) {
                hasTriggeredReInit = true
                readArgsAndInit()
            }
            return
        }

        val needRebuild = layerViews.isEmpty() ||
                layerViews.firstOrNull()?.isAttachedToWindow == false

        if (needRebuild) {
            buildLayerViews(currentState.listData)
            renderLayers(currentState)
            updateAdapters(currentState)
            val scale = if (currentState.isFlipped) -1f else 1f
            layerViews.forEach { it.scaleX = scale }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        timerJob?.cancel()
    }
    override fun bindViewModel() {}

    // ── COMPANION ─────────────────────────────────────────────────────────────

    companion object {
        const val ARG_TEMPLATE_INDEX = "template_index"
        const val ARG_SELECTIONS = "selections"

        fun newArgshow(
            templateIndex: Int,
            targetSelections: ArrayList<SelectionIndex>
        ) = Bundle().apply {
            putInt(ARG_TEMPLATE_INDEX, templateIndex)
            putParcelableArrayList(ARG_SELECTIONS, targetSelections)
        }
    }
}