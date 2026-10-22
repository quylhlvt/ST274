package com.oc.catemoji.catoc.ui.main.show

import android.animation.ValueAnimator
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.MyApplication
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import com.oc.catemoji.catoc.databinding.ActivityShowBinding
import com.oc.catemoji.catoc.ui.main.customize.ColorAdapter
import com.oc.catemoji.catoc.ui.main.customize.NavAdapter
import com.oc.catemoji.catoc.ui.main.customize.PartAdapter
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger

@AndroidEntryPoint
class ShowActivity : BaseActivity<ActivityShowBinding, ShowViewModel>(
    ActivityShowBinding::inflate,
    ShowViewModel::class.java
) {
    // ── Layer views (giống CustomizeActivity) ─────────────────────────────────
    private val layerViews = arrayListOf<AppCompatImageView>()
    private val navToLayerIndex = mutableMapOf<String, Int>()
    private val arrShowColor = mutableListOf<Boolean>()

    private val adapterNav by lazy { NavAdapter() }
    private val adapterColor by lazy { ColorAdapter() }
    private val adapterPart by lazy { PartAdapter() }

    private val pendingLoads = AtomicInteger(0)
    private var timerJob: Job? = null
    private val totalSeconds = 5 * 60
    private var remainingSeconds = totalSeconds
    private var countDownJob: Job? = null
    private var progressAnimator: ValueAnimator? = null
    private var hasFinishedCountDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        remainingSeconds = savedInstanceState?.getInt(KEY_REMAINING_SECONDS) ?: totalSeconds
        hasFinishedCountDown = savedInstanceState?.getBoolean(KEY_COUNTDOWN_FINISHED) ?: false
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_REMAINING_SECONDS, remainingSeconds)
        outState.putBoolean(KEY_COUNTDOWN_FINISHED, hasFinishedCountDown)
        super.onSaveInstanceState(outState)
    }
    private val isCountDownActive: Boolean
        get() = !hasFinishedCountDown
    // ── INFLATE ───────────────────────────────────────────────────────────────
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
            this@ShowActivity,
            getString(R.string.native_cl_cosplaySuccess),
            binding.flNativeCollab
        )
    }

    override fun onScreenStart() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        initNativeCollab()
        if (binding.win.isVisible) return

        when {
            hasFinishedCountDown && remainingSeconds > 0 && timerJob?.isActive != true -> {
                startTimer()
            }
            !hasFinishedCountDown && countDownJob?.isActive != true -> {
                startCountDown()
            }
        }
    }

    override fun onScreenStop() {
        timerJob?.cancel()
        countDownJob?.cancel()
        progressAnimator?.cancel()
        binding.tvCountDown.animate().cancel()
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }
    override fun initView() {
        binding.txtTitle.isSelected = true
        binding.txtTitle2.isSelected = true
        initNativeCollab()
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
//            setImageActionBar(btnActionBarRight, R.drawable.next_app)
            setTextActionBar( tvCenter, "05:00 " )
        }
        setupAdapters()
        readArgsAndInit()
        if (hasFinishedCountDown) {
            binding.countDown.gone()
        }
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
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (remainingSeconds > 0) {
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                binding.actionBar.tvCenter.text =
                    String.format("%02d:%02d ", minutes, seconds)
                kotlinx.coroutines.delay(1000)
                remainingSeconds--
            }
            // Hết giờ → navigate
            binding.actionBar.tvCenter.text = "00:00 "
            if (isAdded && !isDetached) {
                navigateToSuccess()
            }
        }
    }
    private fun startCountDown() {
        countDownJob?.cancel()
        binding.actionBar.btnActionBarLeft.isEnabled = false
        binding.actionBar.btnActionBarRight.isEnabled = false
        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.app_color5), // 3
            ContextCompat.getColor(requireContext(), R.color.app_color6), // 2
            ContextCompat.getColor(requireContext(), R.color.orange_F6)   // 1
        )

        binding.countDown.visibility = View.VISIBLE

        countDownJob = viewLifecycleOwner.lifecycleScope.launch {
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
            hasFinishedCountDown = true
            startTimer()
        }
    }
    private var initializeJob: Job? = null

    private fun readArgsAndInit() {
        initializeJob?.cancel()
        initializeJob = lifecycleScope.launch {
            viewModelActivity.localDataReady.first { it }
            initializeFromArguments()
        }
    }

    private fun initializeFromArguments() {
        val templateId = arguments?.getString(ARG_TEMPLATE_ID)
        val templates = viewModelActivity.templates.value
        val templateIndex = if (templateId != null) {
            templates.indexOfFirst { it.id == templateId }
        } else {
            arguments?.getInt(ARG_TEMPLATE_INDEX, 0) ?: 0
        }
        if (templateIndex !in templates.indices) {
            showToast(getString(R.string.errorcontent))
            activityRouter().navigateUp()
            return
        }
        val targetSelections: ArrayList<SelectionIndex> =
            arguments?.getParcelableArrayList(ARG_SELECTIONS) ?: return

        viewModel.init(templateIndex, targetSelections)
    }

    private fun setupAdapters() {
        binding.rcvNav.adapter = adapterNav
        binding.rcvColor.adapter = adapterColor
        (binding.rcvPart.layoutManager as? GridLayoutManager)?.spanCount = MyApplication.editorGridSpanCount
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
        val userBitmap = renderLayersToBitmap()
        if (userBitmap != null) {
            viewModelActivity.userResultBitmap = userBitmap
            // Ảnh nhân vật mà người dùng vừa chọn/tạo.
            binding.imvImage3.setImageBitmap(userBitmap)
        }

        val cosplayBitmap = viewModelActivity.cosplayBitmap
        if (cosplayBitmap != null && !cosplayBitmap.isRecycled) {
            // Ảnh mẫu người dùng đã xem để cosplay theo.
            binding.imvImage4.setImageBitmap(cosplayBitmap)
        }

        viewModelActivity.cosplayPercent = viewModel.state.value.matchPercent
        binding.win.visible()
        binding.txtTitle2.text = "${viewModel.state.value.matchPercent}% "+ getString(R.string.match)
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

            btnNext.onClick{
                showInter {
                    startActivity(
                        android.content.Intent(
                            requireContext(),
                            com.oc.catemoji.catoc.ui.main.successcosplay.SuccessCosplayActivity::class.java
                        )
                    )
                }
            }

            materialSmall.onClick {
                if (!isCountDownActive) imgShowBig.visible()
            }
            close.onClick {
                if (!isCountDownActive) imgShowBig.gone()
            }

            // ── Random ────────────────────────────────────────────────────────────
            imgRandom.onClick {
                if (isCountDownActive) return@onClick
                if (!checkOnlineNetworkOrShowDialog()) viewModel.randomizeAll() // ← guard
            }

            // ── Color toggle ──────────────────────────────────────────────────────
            imgChangColor.onClick {
                if (isCountDownActive) return@onClick
                if (checkOnlineNetworkOrShowDialog()) return@onClick              // ← guard
                val navPos = viewModel.state.value.currentNavIndex
                if (!viewModel.state.value.hasMultipleColors) return@onClick
                if (navPos !in arrShowColor.indices) return@onClick

                val shouldShow = !arrShowColor[navPos]
                arrShowColor[navPos] = shouldShow
                llColor.animate().cancel()

                if (shouldShow) {
                    llColor.visibility = View.VISIBLE
                    llColor.alpha = 0f
                    llColor.animate().alpha(1f).setDuration(200).start()
                } else {
                    llColor.animate().alpha(0f).setDuration(200).withEndAction {
                        llColor.visibility = View.GONE
                    }.start()
                }
            }
        }

        // ── Nav ───────────────────────────────────────────────────────────────────
        adapterNav.onClick = navClick@{
            if (isCountDownActive) return@navClick
            if (!checkOnlineNetworkOrShowDialog()) viewModel.selectNav(it)       // ← guard
        }

        // ── Color ─────────────────────────────────────────────────────────────────
        adapterColor.onClick = colorClick@{
            if (isCountDownActive) return@colorClick
            if (!checkOnlineNetworkOrShowDialog()) viewModel.selectColor(it)     // ← guard
        }

        // ── Part ──────────────────────────────────────────────────────────────────
        adapterPart.onClick = partClick@{ idx, type ->
            if (isCountDownActive) return@partClick
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

    // ShowActivity.observeData() — THÊM guard này
    // ✅ FIX — thêm flag giống CustomizeActivity
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

    // ── BUILD LAYER VIEWS (giống CustomizeActivity.buildLayerViews) ───────────

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

    // ── RENDER LAYERS (giống CustomizeActivity.renderLayers) ─────────────────

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

    // ── ADAPTERS (giống CustomizeActivity.updateAdapters) ────────────────────

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
        val safePercent = percent.coerceIn(0, 100)
        binding.tvPercent.text = "$safePercent%"

        val anchor = binding.imgStarAnchor
        val targetBias = 1f - safePercent / 100f
        val currentBias = (anchor.layoutParams as ConstraintLayout.LayoutParams).verticalBias
        progressAnimator?.cancel()
        if (currentBias == targetBias) return

        progressAnimator = ValueAnimator.ofFloat(currentBias, targetBias).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                anchor.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    verticalBias = animator.animatedValue as Float
                }
            }
            start()
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
            countDownJob?.cancel()
            remainingSeconds = totalSeconds
            hasFinishedCountDown = false
            binding.win.gone()
            binding.imgShowBig.gone()
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
            binding.actionBar.tvCenter.text = "05:00 "
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
    override fun onScreenDestroyed() {
        progressAnimator?.cancel()
        progressAnimator = null
        super.onScreenDestroyed()
        timerJob?.cancel()
        countDownJob?.cancel()
    }
    override fun bindViewModel() {}

    // ── COMPANION ─────────────────────────────────────────────────────────────

    companion object {
        private const val KEY_REMAINING_SECONDS = "remainingSeconds"
        private const val KEY_COUNTDOWN_FINISHED = "countdownFinished"
        const val ARG_TEMPLATE_INDEX = "template_index"
        const val ARG_TEMPLATE_ID = "template_id"
        const val ARG_SELECTIONS = "selections"

        fun newArgshow(
            templateIndex: Int,
            targetSelections: ArrayList<SelectionIndex>,
            templateId: String? = null
        ) = Bundle().apply {
            putInt(ARG_TEMPLATE_INDEX, templateIndex)
            templateId?.let { putString(ARG_TEMPLATE_ID, it) }
            putParcelableArrayList(ARG_SELECTIONS, targetSelections)
        }
    }
}
