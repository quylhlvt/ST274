package com.oc.catemoji.catoc.ui.main.customize

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.saveToFile
import com.oc.catemoji.catoc.core.extention.setFrameActionBar
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.showRewardAds1
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.LayerTransform
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import com.oc.catemoji.catoc.databinding.FragmentCustomizeBinding
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

@AndroidEntryPoint
class CustomizeFragment : BaseFragment<FragmentCustomizeBinding, CustomizeViewModel>(
    FragmentCustomizeBinding::inflate,
    CustomizeViewModel::class.java
), BackPressHandler {
    private val arrShowColor = mutableListOf<Boolean>()
    private var isScaleActive = false

    private var isColorVisible = true

    // viewModel đã được inject sẵn bởi BaseFragment — không cần khai báo lại
    // sharedViewModel dùng viewModelActivity từ BaseFragment
    private val sharedViewModel: ViewModelActivity get() = viewModelActivity

    private val layerViews = arrayListOf<AppCompatImageView>()
    private val navToLayerIndex = mutableMapOf<String, Int>()
    private val layerDrawableCache = mutableMapOf<String, Drawable.ConstantState>()

    private val adapterNav by lazy { NavAdapter() }
    private val adapterColor by lazy { ColorAdapter() }
    private val adapterPart by lazy { PartAdapter() }

    private val pendingLoads = AtomicInteger(0)
    private var canSave = false
    private var hasTriggeredReInit = false
    private var shouldScrollPartToSelection = true
    private var lastNavIndex = RecyclerView.NO_POSITION

    // ── INFLATE ───────────────────────────────────────────────────────────────

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentCustomizeBinding = FragmentCustomizeBinding.inflate(inflater, container, false)

    private fun initNativeCollab() {
        Log.d("NativeCollab", "🔄 initNativeCollab called, stack: ${Thread.currentThread().stackTrace[3]}")
        Admob.getInstance().loadNativeCollapNotBanner(requireContext(), getString(R.string.native_cl_custom), binding.flNativeCollab)
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

    override fun onDestroyView() {
        isScaleActive = false
        super.onDestroyView()
    }

    // ── INIT ──────────────────────────────────────────────────────────────────
    private fun isOnlineTemplate(): Boolean {
        val templateIndex = arguments?.getInt(ARG_TEMPLATE_INDEX, 0) ?: 0
        val templateId = arguments?.getString(ARG_TEMPLATE_ID)
        val templates = sharedViewModel.templates.value
        val resolvedIndex = if (templateId != null) {
            templates.indexOfFirst { it.id == templateId }.takeIf { it >= 0 } ?: templateIndex
        } else templateIndex
        return templates.getOrNull(resolvedIndex)?.id?.startsWith("online_") == true
    }

    /** Trả về true nếu đã show dialog → caller nên block action */
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

    override fun initView() {
        initNativeCollab()
        binding.txtReset.isSelected = true
        binding.txtSize.isSelected = true
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setImageActionBar(btnActionBarCenter2, R.drawable.ic_reset_all_custom)
            setImageActionBar(btnActionBarCenter, R.drawable.ic_flip_all_custom)
            setFrameActionBar(btnActionBarRightText,tvRightText,getString(R.string.next ))

        }
        setupAdapters()

        readArgsAndInit()
    }

    // CustomizeFragment.kt - readArgsAndInit() — FIX chính ở đây
    private fun readArgsAndInit() {
        val templateIndex = arguments?.getInt(ARG_TEMPLATE_INDEX, 0) ?: 0
        val templateId = arguments?.getString(ARG_TEMPLATE_ID) // ✅ id để verify
        val isEdit = arguments?.getBoolean(ARG_IS_EDIT, false) ?: false
        val isFlipped = arguments?.getBoolean(ARG_IS_FLIPPED, false) ?: false
        val customizedId = arguments?.getString(ARG_CUSTOMIZED_ID)

        val savedSelections: ArrayList<SelectionIndex>? =
            arguments?.getParcelableArrayList(ARG_SELECTIONS)

        val templates = sharedViewModel.templates.value

        // ✅ Resolve index đúng bằng id nếu có
        val resolvedIndex = if (templateId != null) {
            val byId = templates.indexOfFirst { it.id == templateId }
            if (byId >= 0) byId else templateIndex // fallback về index nếu không tìm được
        } else {
            templateIndex
        }

        // ✅ Guard cuối
        if (resolvedIndex < 0 || resolvedIndex >= templates.size) {
            showToast(getString(R.string.download_failed_please_try_again_later))
            findNavController().navigateUp()
            return
        }

        when {
            isEdit && savedSelections != null -> {
                viewModel.initEditWithCustomizedId(
                    templateIndex = resolvedIndex,
                    customizedId = customizedId ?: "",
                    savedSelections = savedSelections,
                    isFlipped = isFlipped
                )
            }

            savedSelections != null -> {
                viewModel.initWithSelections(resolvedIndex, savedSelections)
            }

            else -> {
                viewModel.initNew(resolvedIndex)
            }
        }
    }

    private fun setupAdapters() {
        binding.rcvNav.adapter = adapterNav
        binding.rcvColor.adapter = adapterColor
        binding.rcvPart.adapter = adapterPart
    }

    // ── ACTIONS ───────────────────────────────────────────────────────────────
    override fun viewListener() {
        binding.imgScale.onClick {
            if (viewModel.resolvePathAt(viewModel.state.value.currentNavIndex) == null) {
                return@onClick
            }
            isScaleActive = !isScaleActive
            updateScalePanel()
        }
        binding.ratioPlus.onClickAndHold {
            changeCurrentTransform { it.copy(scale = it.scale + SCALE_STEP) }
        }
        binding.ratioMinus.onClickAndHold {
            changeCurrentTransform { it.copy(scale = it.scale - SCALE_STEP) }
        }
        binding.ratioRight.onClickAndHold {
            changeCurrentTransform { it.copy(rotation = normalizeRotation(it.rotation + ROTATION_STEP)) }
        }
        binding.ratioLeft.onClickAndHold {
            changeCurrentTransform { it.copy(rotation = normalizeRotation(it.rotation - ROTATION_STEP)) }
        }
        binding.transitionLeft.onClickAndHold {
            changeCurrentTransform { it.copy(translationX = it.translationX - TRANSLATION_STEP) }
        }
        binding.transitionRight.onClickAndHold {
            changeCurrentTransform { it.copy(translationX = it.translationX + TRANSLATION_STEP) }
        }
        binding.transitionTop.onClickAndHold {
            changeCurrentTransform { it.copy(translationY = it.translationY - TRANSLATION_STEP) }
        }
        binding.transitionBottom.onClickAndHold {
            changeCurrentTransform { it.copy(translationY = it.translationY + TRANSLATION_STEP) }
        }
        binding.btnResetScale.onClick {
            viewModel.resetTransform(viewModel.state.value.currentNavIndex)
            applyTransformsToAllLayers()
            syncTransformControls()
        }
        adapterNav.onClick = {
            if (!checkOnlineNetworkOrShowDialog()) {
                shouldScrollPartToSelection = true
                viewModel.selectNav(it)
            }
        }
        adapterColor.onClick = {
            if (!checkOnlineNetworkOrShowDialog()) viewModel.selectColor(it)
        }
        adapterPart.onClick = { idx, type ->
            if (!checkOnlineNetworkOrShowDialog()) {
                when (type) {
                    "none" -> {
                        shouldScrollPartToSelection = false
                        viewModel.selectNone()
                    }
                    "dice" -> {
                        shouldScrollPartToSelection = true
                        viewModel.selectDiceCurrent()
                    }

                    else -> {
                        shouldScrollPartToSelection = false
                        viewModel.selectPath(idx)
                    }
                }
            }
        }

        binding.apply {
//            end.onClick {
//                val navPos = viewModel.state.value.currentNavIndex
//                if (navPos < arrShowColor.size) arrShowColor[navPos] = false
//                llColor.animate().alpha(0f).setDuration(200).withEndAction {
//                    llColor.visibility = View.INVISIBLE
//                }.start()
//            }
            imgChangColor.onClick {
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
            imgRandom.onClick {
                if (!checkOnlineNetworkOrShowDialog()) {
                    if (viewModel.state.value.randomCount == 0) {
                        randomizeAllParts()
                    } else {
                        showConfirmDialog(
                            title = getString(R.string.random_cat),
                            message = getString(R.string.watch_ad_to_randomize_cat),
                            noText = getString(R.string.watch_ad),
                            yesText = getString(R.string.cancel),
                            onNo = {
                                showRewardAds1(
                                onRewardSuccess = { randomizeAllParts()},
                                onAdClosed = {})
                            },
                        onYes = {}
                        )
                    }
                }
            }
            actionBar.btnActionBarCenter2.setOnClickListener {
                if (!checkOnlineNetworkOrShowDialog()){
                showConfirmDialog(
                    title = getString(R.string.reset),
                    message = getString(R.string.do_you_want_to_reset_all),
                    onYes = {
                        showInter {
                            arrShowColor.fill(true);
                            shouldScrollPartToSelection = true
                            viewModel.resetAll()
                            applyTransformsToAllLayers(viewModel.state.value)
                            syncTransformControls()
                        }
                    }
                )
            }}
            actionBar.btnActionBarCenter.onClick { viewModel.toggleFlip() }
            actionBar.btnActionBarRightText.onClick {
                if (!canSave) return@onClick
                if (!isAdded || isDetached || view == null) return@onClick
                if (checkOnlineNetworkOrShowDialog()) return@onClick
                if (canSave)
                    showInter {
                        performSave()
                    }
            }
            actionBar.btnActionBarLeft.setOnClickListener { confirmExit() }
        }
    }

    private fun randomizeAllParts() {
        if (!isAdded || isDetached || view == null) return
        shouldScrollPartToSelection = true
        viewModel.randomizeAll()
    }

    // ── OBSERVE ───────────────────────────────────────────────────────────────
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
                    hasTriggeredReInit = false  // reset khi state đã có data

                    if (layerViews.size != state.listData.size) {
                        buildLayerViews(state.listData)
                    }
                    renderLayers(state)
                    updateAdapters(state)
                    applyTransformsToAllLayers(state)
                }
            }
        }
    }

    // ── LAYER VIEWS ───────────────────────────────────────────────────────────

    private fun buildLayerViews(parts: List<BodyPartModel>) {
        val currentFlipped = viewModel.state.value.isFlipped  // ✅ lấy flip state hiện tại
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
                scaleX = if (currentFlipped) -1f else 1f  // ✅ apply flip ngay khi tạo view
            }
            binding.rlCharacter.addView(iv)
            layerViews.add(iv)
            navToLayerIndex[bp.nav] = layerIdx
        }
    }

    private fun applyTransformsToAllLayers(state: CustomizeState = viewModel.state.value) {
        state.listData.forEachIndexed { navIndex, bodyPart ->
            val layerIndex = navToLayerIndex[bodyPart.nav] ?: return@forEachIndexed
            val imageView = layerViews.getOrNull(layerIndex) ?: return@forEachIndexed
            val transform = viewModel.getTransform(navIndex)
            imageView.scaleX = transform.scale * if (state.isFlipped) -1f else 1f
            imageView.scaleY = transform.scale
            imageView.translationX = transform.translationX
            imageView.translationY = transform.translationY
            imageView.rotation = transform.rotation
        }
    }

    private fun changeCurrentTransform(block: (LayerTransform) -> LayerTransform) {
        val navIndex = viewModel.state.value.currentNavIndex
        if (viewModel.resolvePathAt(navIndex) == null) return
        val requested = block(viewModel.getTransform(navIndex))
        val maxX = binding.rlCharacter.width / 2f
        val maxY = binding.rlCharacter.height / 2f
        viewModel.updateTransform(
            navIndex,
            requested.copy(
                scale = requested.scale.coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE),
                translationX = requested.translationX.coerceIn(-maxX, maxX),
                translationY = requested.translationY.coerceIn(-maxY, maxY)
            )
        )
        applyTransformsToAllLayers()
        syncTransformControls()
    }

    private fun normalizeRotation(value: Float): Float = when {
        value >= 360f -> value - 360f
        value <= -360f -> value + 360f
        else -> value
    }

    private fun syncTransformControls() {
        val navIndex = viewModel.state.value.currentNavIndex
        val hasVisibleLayer = viewModel.resolvePathAt(navIndex) != null

        // INVISIBLE keeps the color row layout stable, while hiding Scale for "none".
        binding.imgScale.visibility = if (hasVisibleLayer) View.VISIBLE else View.INVISIBLE
        if (!hasVisibleLayer) isScaleActive = false
        updateScalePanel()

        val transform = viewModel.getTransform(navIndex)
        binding.btnResetScale.isEnabled = hasVisibleLayer && !viewModel.isTransformDefault(navIndex)
        binding.btnResetScale.alpha = if (binding.btnResetScale.isEnabled) 1f else DISABLED_CONTROL_ALPHA

        setTransformControlEnabled(binding.ratioPlus, hasVisibleLayer && transform.scale < MAX_LAYER_SCALE)
        setTransformControlEnabled(binding.ratioMinus, hasVisibleLayer && transform.scale > MIN_LAYER_SCALE)
        setTransformControlEnabled(binding.ratioLeft, hasVisibleLayer)
        setTransformControlEnabled(binding.ratioRight, hasVisibleLayer)

        val maxTranslationX = binding.rlCharacter.width / 2f
        val maxTranslationY = binding.rlCharacter.height / 2f
        if (maxTranslationX <= 0f || maxTranslationY <= 0f) {
            setTransformControlEnabled(binding.transitionLeft, hasVisibleLayer)
            setTransformControlEnabled(binding.transitionRight, hasVisibleLayer)
            setTransformControlEnabled(binding.transitionTop, hasVisibleLayer)
            setTransformControlEnabled(binding.transitionBottom, hasVisibleLayer)
        } else {
            setTransformControlEnabled(
                binding.transitionLeft,
                hasVisibleLayer && transform.translationX > -maxTranslationX
            )
            setTransformControlEnabled(
                binding.transitionRight,
                hasVisibleLayer && transform.translationX < maxTranslationX
            )
            setTransformControlEnabled(
                binding.transitionTop,
                hasVisibleLayer && transform.translationY > -maxTranslationY
            )
            setTransformControlEnabled(
                binding.transitionBottom,
                hasVisibleLayer && transform.translationY < maxTranslationY
            )
        }
    }

    private fun updateScalePanel() {
        binding.imgScale.setImageResource(
            if (isScaleActive) R.drawable.ic_scale_cus_selected else R.drawable.ic_scale_cus
        )
        binding.frameScale.visibility =
            if (isScaleActive && binding.imgScale.visibility == View.VISIBLE) View.VISIBLE else View.GONE
    }

    private fun setTransformControlEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else DISABLED_CONTROL_ALPHA
    }

    // Reset pendingLoads mỗi khi bắt đầu render lại
    private fun renderLayers(state: CustomizeState) {
        // ✅ Reset counter trước khi đếm lại
        val pathsToLoad = state.listData.mapIndexedNotNull { i, bp ->
            val path = viewModel.resolvePathAt(i)
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
            // Không có gì cần load → enable save ngay
            setSaveEnabled(true)
            return
        }

        // Reset counter chính xác theo số ảnh thực sự cần load
        pendingLoads.set(pathsToLoad.size)
        setSaveEnabled(false)

        pathsToLoad.forEach { (view, path, _) ->
            view.tag = path
            view.visibility = View.VISIBLE
            applyTransformsToAllLayers()
            loadImageIntoView(view, path, skipCount = true) // skipCount vì đã set ở trên
        }
    }

    // Thêm param skipCount để tránh double increment
    private fun loadImageIntoView(view: ImageView, path: String, skipCount: Boolean = false) {
        if (!skipCount) {
            pendingLoads.incrementAndGet()
            setSaveEnabled(false)
        }

        // Fragment vẫn nằm trong back stack khi mở AddCharacter. Dùng lại drawable
        // đã hiển thị để không phụ thuộc mạng khi quay về Custom.
        if (!isInternetAvailable(requireContext())) {
            layerDrawableCache[path]?.newDrawable(resources)?.mutate()?.let { cached ->
                view.setImageDrawable(cached)
                onLoadFinished()
                return
            }
        }

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
                    resource?.constantState?.let { layerDrawableCache[path] = it }
                    onLoadFinished(); return false
                }
            })
            .into(view)
    }

    private fun onLoadFinished() {
        if (pendingLoads.decrementAndGet() <= 0) {
            pendingLoads.set(0)
            view?.post {
                setSaveEnabled(true)
                viewModel.onLoadingComplete()
            }
        }
    }

    // ✅ Thêm vào onResume: reset pendingLoads khi quay lại
    override fun onResume() {
        super.onResume()
        pendingLoads.set(0)

        val state = viewModel.state.value
        if (state.listData.isEmpty()) {
            if (!hasTriggeredReInit) {
                hasTriggeredReInit = true
                readArgsAndInit()
            }
            return
        }
        // ✅ Nếu state empty thì để observeData xử lý re-init

        val needRebuild = layerViews.isEmpty() ||
                layerViews.firstOrNull()?.isAttachedToWindow == false

        if (needRebuild) {
            layerViews.clear()
            navToLayerIndex.clear()
            binding.rlCharacter.removeAllViews()
            buildLayerViews(state.listData)
            renderLayers(state)
            updateAdapters(state)
            applyTransformsToAllLayers(state)
        } else {
            // ✅ Force re-render để reload ảnh bị mất khỏi memory
            layerViews.forEach { it.tag = null }
            renderLayers(state)
        }
    }

    private fun setSaveEnabled(enabled: Boolean) {
        canSave = enabled
        binding.actionBar.btnActionBarRightText.alpha = if (enabled) 1f else 0.5f
        binding.actionBar.btnActionBarRightText.isEnabled = enabled
    }

    // ── ADAPTERS ──────────────────────────────────────────────────────────────

    private fun updateAdapters(state: CustomizeState) {
        val navChanged = lastNavIndex != state.currentNavIndex
        if (navChanged) lastNavIndex = state.currentNavIndex

        adapterNav.setPos(state.currentNavIndex)
        adapterNav.submitList(state.listData)
        binding.imgChangColor.isVisible = state.hasMultipleColors

        // ── Color ──────────────────────────────────────────────────────────────
        adapterColor.setPos(state.currentColorIndex)

        // Khởi tạo arrShowColor khi data load lần đầu
        if (arrShowColor.size != state.listData.size) {
            arrShowColor.clear()
            repeat(state.listData.size) { arrShowColor.add(true) }
        }

        val navPos = state.currentNavIndex

        if (state.hasMultipleColors) {
            adapterColor.submitList(state.currentColors)
            binding.rcvColor.post {
                binding.rcvColor.smoothScrollToPosition(state.currentColorIndex)
            }
            // Y hệt updateColorSectionVisibility trong Activity
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

        // ── Part ───────────────────────────────────────────────────────────────
        val bp = state.listData.getOrNull(state.currentNavIndex)
        val thumb = buildThumbList(bp, state.currentPaths)
        adapterPart.listThumb = thumb
        adapterPart.setPos(state.currentPathIndex)

        val targetPartIndex = state.currentPathIndex.coerceAtLeast(0)
        adapterPart.submitList(state.currentPaths)
        if (shouldScrollPartToSelection || navChanged) {
            binding.rcvPart.post {
                binding.rcvPart.smoothScrollToPosition(targetPartIndex)
            }
        }
        shouldScrollPartToSelection = false
        syncTransformControls()
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

    // ── SAVE ──────────────────────────────────────────────────────────────────

    private fun performSave() {
        if (!canSave) return
        setSaveEnabled(false)
        showLoadingSafe()

        viewLifecycleOwner.lifecycleScope.launch {
            // Android Views must be read and drawn on the main thread.
            val bitmap = renderLayersToBitmap()
            if (bitmap == null) {
                setSaveEnabled(true)
                hideLoadingSafe()
                return@launch
            }
            viewModelActivity.customizeBitmap = bitmap
            val savedPath = withContext(Dispatchers.IO) {
                bitmap.saveToFile(requireActivity(), "avatar")
            }

            if (savedPath == null) {
                setSaveEnabled(true)
                hideLoadingSafe()
                return@launch
            }

            val result = viewModel.onSaveComplete(savedPath)
            result?.let { (template, selections) ->
                sharedViewModel.saveCharacterWithSelections(
                    character = template,
                    selections = selections,
                    imageSave = savedPath,
                    isFlipped = viewModel.state.value.isFlipped,
                    layerTransforms = viewModel.layerTransforms.value
                )
            }

            val isEdit = arguments?.getBoolean(ARG_IS_EDIT, false) ?: false
            if (isEdit) {
                runCatching {
                    findNavController()
                        .getBackStackEntry(R.id.viewFragment)
                        .savedStateHandle["updated_image_path"] = savedPath
                }
            }

            // ✅ Navigate trực tiếp trên Main thread, KHÔNG wrap thêm withContext
            if (isAdded && !isDetached) {
                findNavController().navigate(
                    R.id.action_customizeFragment_to_addFragment,
                    Bundle().apply { putString("imagePath", savedPath) }
                )
            }
        }
    }

    private fun renderLayersToBitmap(): Bitmap? {
        val root = binding.rlCharacter
        if (root.width == 0 || root.height == 0) return null

        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        layerViews.forEachIndexed { layerIndex, iv ->
            if (iv.visibility != View.VISIBLE) return@forEachIndexed

            val navIndex = navToLayerIndex.entries
                .firstOrNull { it.value == layerIndex }
                ?.key
                ?.let { nav -> viewModel.state.value.listData.indexOfFirst { it.nav == nav } }
                ?.takeIf { it >= 0 }
                ?: return@forEachIndexed
            val transform = viewModel.getTransform(navIndex)

            canvas.save()
            canvas.translate(
                root.width / 2f + transform.translationX,
                root.height / 2f + transform.translationY
            )
            canvas.scale(
                transform.scale * if (viewModel.state.value.isFlipped) -1f else 1f,
                transform.scale
            )
            canvas.rotate(transform.rotation)
            canvas.translate(-root.width / 2f, -root.height / 2f)

            val oldScaleX = iv.scaleX
            val oldScaleY = iv.scaleY
            val oldTranslationX = iv.translationX
            val oldTranslationY = iv.translationY
            val oldRotation = iv.rotation
            iv.scaleX = 1f
            iv.scaleY = 1f
            iv.translationX = 0f
            iv.translationY = 0f
            iv.rotation = 0f
            iv.draw(canvas)
            iv.scaleX = oldScaleX
            iv.scaleY = oldScaleY
            iv.translationX = oldTranslationX
            iv.translationY = oldTranslationY
            iv.rotation = oldRotation

            canvas.restore()
        }

        return bitmap
    }

    private fun View.onClickAndHold(action: () -> Unit) {
        var repeatJob: Job? = null
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isEnabled) return@setOnTouchListener true
                    action()
                    repeatJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(400)
                        while (isEnabled) {
                            action()
                            delay(80)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeatJob?.cancel()
                    repeatJob = null
                    performClick()
                    true
                }

                else -> false
            }
        }
    }
    // ── BASE OVERRIDES ────────────────────────────────────────────────────────

    override fun bindViewModel() {}

    //--------------------------------Backpress
    override fun onBackPressed(): Boolean {
        confirmExit()
        return true
    }

    // ── COMPANION ─────────────────────────────────────────────────────────────
    private fun confirmExit() {
        showConfirmDialog(
            message = getString(R.string.haven_t_saved_it_yet_do_you_want_to_exit),
            title = getString(R.string.exit),
            onYes = {
                showInter {
                    hideLoadingSafe()
                    findNavController().navigateUp()
                }
            },
            onNo = { hideLoadingSafe() }
        )
    }

    companion object {
        private const val MIN_LAYER_SCALE = 0.3f
        private const val MAX_LAYER_SCALE = 2f
        private const val SCALE_STEP = 0.05f
        private const val ROTATION_STEP = 5f
        private const val TRANSLATION_STEP = 20f
        private const val DISABLED_CONTROL_ALPHA = 0.4f
        const val ARG_TEMPLATE_INDEX = "template_index"
        const val ARG_TEMPLATE_ID = "template_id"
        const val ARG_IS_EDIT = "is_edit"
        const val ARG_IS_FLIPPED = "is_flipped"
        const val ARG_SELECTIONS = "selections"
        const val ARG_CUSTOMIZED_ID = "customized_id"

        fun newArgs(
            templateIndex: Int,
            templateId: String? = null,
            isEdit: Boolean = false,
            customizedId: String? = null,
            savedSelections: ArrayList<SelectionIndex>? = null,
            isFlipped: Boolean = false
        ) = Bundle().apply {
            putInt(ARG_TEMPLATE_INDEX, templateIndex)
            templateId?.let { putString(ARG_TEMPLATE_ID, it) }
            putBoolean(ARG_IS_EDIT, isEdit)
            putBoolean(ARG_IS_FLIPPED, isFlipped)
            customizedId?.let { putString(ARG_CUSTOMIZED_ID, it) }
            savedSelections?.let { putParcelableArrayList(ARG_SELECTIONS, it) }
        }
    }
}
