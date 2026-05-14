package com.oc.catemoji.catoc.ui.main.customize

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
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
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.saveToFile
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import com.oc.catemoji.catoc.databinding.FragmentCustomizeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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

    private var isColorVisible = true

    // viewModel đã được inject sẵn bởi BaseFragment — không cần khai báo lại
    // sharedViewModel dùng viewModelActivity từ BaseFragment
    private val sharedViewModel: ViewModelActivity get() = viewModelActivity

    private val layerViews = arrayListOf<AppCompatImageView>()
    private val navToLayerIndex = mutableMapOf<String, Int>()

    private val adapterNav by lazy { NavAdapter() }
    private val adapterColor by lazy { ColorAdapter() }
    private val adapterPart by lazy { PartAdapter() }

    private val pendingLoads = AtomicInteger(0)
    private var canSave = false
    private var hasTriggeredReInit = false

    // ── INFLATE ───────────────────────────────────────────────────────────────

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentCustomizeBinding = FragmentCustomizeBinding.inflate(inflater, container, false)

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
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setImageActionBar(btnActionBarCenter, R.drawable.ic_reset_all_custom)
            setImageActionBar(btnActionBarCenter2, R.drawable.ic_flip_all_custom)
            setImageActionBar(btnActionBarRight, R.drawable.next_app)
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
        adapterNav.onClick = {
            if (!checkOnlineNetworkOrShowDialog()) viewModel.selectNav(it)
        }
        adapterColor.onClick = {
            if (!checkOnlineNetworkOrShowDialog()) viewModel.selectColor(it)
        }
        adapterPart.onClick = { idx, type ->
            if (!checkOnlineNetworkOrShowDialog()) {
                when (type) {
                    "none" -> viewModel.selectNone()
                    "dice" -> viewModel.selectDiceCurrent()
                    else   -> viewModel.selectPath(idx)
                }
            }
        }

        binding.apply {
            end.onClick {
                val navPos = viewModel.state.value.currentNavIndex
                if (navPos < arrShowColor.size) arrShowColor[navPos] = false
                llColor.animate().alpha(0f).setDuration(200).withEndAction {
                    llColor.visibility = View.INVISIBLE
                }.start()
            }
            imgChangColor.onClick {
                val navPos = viewModel.state.value.currentNavIndex
                if (!viewModel.state.value.hasMultipleColors) return@onClick
                if (navPos < arrShowColor.size) arrShowColor[navPos] = true
                if (llColor.isVisible) return@onClick
                llColor.visibility = View.VISIBLE
                llColor.alpha = 0f
                llColor.animate().alpha(1f).setDuration(200).start()
            }
            imgRandom.onClick {
                if (!checkOnlineNetworkOrShowDialog()) viewModel.randomizeAll()
            }
            actionBar.btnActionBarCenter.setOnClickListener {
                showConfirmDialog(
                    title = getString(R.string.reset),
                    message = getString(R.string.do_you_want_to_reset_all),
                    onYes = { arrShowColor.fill(true); viewModel.resetAll() }
                )
            }
            actionBar.btnActionBarCenter2.setOnClickListener { viewModel.toggleFlip() }
            actionBar.btnActionBarRight.setOnClickListener { if (canSave) performSave() }
            actionBar.btnActionBarLeft.setOnClickListener { confirmExit() }
        }
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
                    val scale = if (state.isFlipped) -1f else 1f
                    layerViews.forEach { it.scaleX = scale }
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
            view.scaleX = if (viewModel.state.value.isFlipped) -1f else 1f
            loadImageIntoView(view, path, skipCount = true) // skipCount vì đã set ở trên
        }
    }

    // Thêm param skipCount để tránh double increment
    private fun loadImageIntoView(view: ImageView, path: String, skipCount: Boolean = false) {
        if (!skipCount) {
            pendingLoads.incrementAndGet()
            setSaveEnabled(false)
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
            val scale = if (state.isFlipped) -1f else 1f
            layerViews.forEach { it.scaleX = scale }
        } else {
            // ✅ Force re-render để reload ảnh bị mất khỏi memory
            layerViews.forEach { it.tag = null }
            renderLayers(state)
        }
    }

    private fun setSaveEnabled(enabled: Boolean) {
        canSave = enabled
        binding.actionBar.btnActionBarRight.alpha = if (enabled) 1f else 0.5f
        binding.actionBar.btnActionBarRight.isEnabled = enabled
    }

    // ── ADAPTERS ──────────────────────────────────────────────────────────────

    private fun updateAdapters(state: CustomizeState) {
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
        binding.rcvPart.post {
            binding.rcvPart.smoothScrollToPosition(targetPartIndex)
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

    // ── SAVE ──────────────────────────────────────────────────────────────────

    private fun performSave() {
        if (!canSave) return
        setSaveEnabled(false)
        showLoadingSafe()

        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {  // ✅ Đổi sang Default thay vì gọi trực tiếp
                renderLayersToBitmap()
            }
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
                    isFlipped = viewModel.state.value.isFlipped
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

        layerViews.forEach { iv ->
            if (iv.visibility != View.VISIBLE) return@forEach

            canvas.save()
            if (iv.scaleX < 0) {
                canvas.scale(-1f, 1f, root.width / 2f, 0f)
            }

            // ✅ Vẽ trực tiếp từ ImageView — giữ đúng transform matrix của FIT_CENTER
            iv.draw(canvas)

            canvas.restore()
        }

        return bitmap
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
                hideLoadingSafe()
                findNavController().navigateUp()
            },
            onNo = { hideLoadingSafe() }
        )
    }
    companion object {
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