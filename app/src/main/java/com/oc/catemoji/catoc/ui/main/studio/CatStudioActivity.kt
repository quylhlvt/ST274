package com.oc.catemoji.catoc.ui.main.studio

import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.MyApplication
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.core.custom.Draw
import com.oc.catemoji.catoc.core.custom.listener.listenerdraw.OnDrawListener
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.saveToFile
import com.oc.catemoji.catoc.core.extention.setFrameActionBar
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.databinding.ActivityCatStudioBinding
import com.oc.catemoji.catoc.ui.main.add_character.DesignSource
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CatStudioActivity : BaseActivity<ActivityCatStudioBinding, CatStudioViewModel>(
    ActivityCatStudioBinding::inflate,
    CatStudioViewModel::class.java
) {
    private val arrShowColor = mutableListOf<Boolean>()
    private var isScaleActive = false
    private var guidePlayer: ExoPlayer? = null
    private var isSeekingGuide = false
    private var resumeGuideAfterSeek = false
    private var guideLoadFailed = false
    private var guidePlaybackRequested = false
    private var guidePositionMs = 0L
    private var autoGuidePlaybackPending = false
    private var pendingGuideStart: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private val adapterNav by lazy { NavStudioAdapter() }
    private val adapterPart by lazy { PartStudioAdapter() }

    private var canSave = false
    private var hasTriggeredReInit = false
    private var shouldScrollPartToSelection = true
    private var shouldScrollNavToSelection = false
    private var lastNavIndex = RecyclerView.NO_POSITION
    private var lastNavData: List<BodyPartModel>? = null
    private var isNoInternetDialogShowing = false
    private var lastNetworkOnline: Boolean? = null
    private var isRestoringDraft = false
    private var isLayerBitmapLoading = false
    private val activityManager: ActivityManager by lazy {
        requireContext()
            .getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private val isLowRamDevice: Boolean by lazy {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        activityManager.isLowRamDevice ||
            memoryInfo.totalMem <= LOW_RAM_TOTAL_MEMORY_BYTES ||
            activityManager.memoryClass <= LOW_RAM_HEAP_CLASS_MB
    }
    private val isVeryLowRamDevice: Boolean by lazy {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        memoryInfo.totalMem <= VERY_LOW_RAM_TOTAL_MEMORY_BYTES ||
            activityManager.memoryClass <= VERY_LOW_RAM_HEAP_CLASS_MB
    }
    private val canvasBitmapSizePx: Int by lazy {
        when {
            isVeryLowRamDevice -> VERY_LOW_RAM_CANVAS_BITMAP_SIZE_PX
            isLowRamDevice -> LOW_RAM_CANVAS_BITMAP_SIZE_PX
            else -> CANVAS_BITMAP_SIZE_PX
        }
    }

    private fun hasEnoughMemoryForLayer(): Boolean {
        val runtime = Runtime.getRuntime()
        val availableHeap =
            runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        // Glide giữ bitmap decode và Studio tạo thêm một bản bitmap thuộc quyền sở hữu
        // của DrawView. Chỉ chặn khi heap thực sự không đủ cho hai bitmap + vùng đệm.
        val bitmapBytes =
            canvasBitmapSizePx.toLong() * canvasBitmapSizePx * ARGB_8888_BYTES_PER_PIXEL
        val requiredHeap = bitmapBytes * BITMAP_COPIES_WHILE_ADDING + MIN_HEAP_RESERVE_BYTES
        return !memoryInfo.lowMemory && availableHeap >= requiredHeap
    }

    // ── INFLATE ───────────────────────────────────────────────────────────────
    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        val min = total / 60
        val sec = total % 60
        return String.format("%02d:%02d", min, sec)
    }
    private val updateSeek = object : Runnable {
        override fun run() {
            guidePlayer?.let { player ->
                if (player.duration > 0) {
                    binding.seekGuide.max = player.duration.toInt()
                    if (!isSeekingGuide) {
                        binding.seekGuide.progress = player.currentPosition.toInt()
                    }

                    binding.tvGuideTime.text =
                        "${formatTime(player.currentPosition)} / ${formatTime(player.duration)}"
                }
            }
            handler.postDelayed(this, 300)
        }
    }
private fun initNativeCollab() {
        Admob.getInstance().loadNativeCollapNotBanner(this@CatStudioActivity, getString(R.string.native_cl_catStudio), binding.flNativeCollab)
    }

    override fun onScreenStart() {
        if (!isAdded || isDetached) return
        if (guidePlayer != null) {
            handler.removeCallbacks(updateSeek)
            handler.post(updateSeek)
        }
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        initNativeCollab()
    }

    override fun onScreenStop() {
        if (!isAdded || isDetached) return
        // Snapshot thiết kế trước khi app vào background hoặc view bị hệ thống thu hồi.
        if (!isRestoringDraft) {
            viewModel.saveDrawItems(binding.drawView.drawList)
        }
        viewModel.navScrollState = binding.rcvNav.layoutManager?.onSaveInstanceState()
        viewModel.partScrollState = binding.rcvPart.layoutManager?.onSaveInstanceState()
        pendingGuideStart?.let(binding.root::removeCallbacks)
        pendingGuideStart = null
        autoGuidePlaybackPending = false
        guidePlaybackRequested = false
        resumeGuideAfterSeek = false
        guidePlayer?.let { player ->
            guidePositionMs = player.currentPosition.coerceAtLeast(0L)
            player.pause()
            player.release()
        }
        binding.playerViewGuide.player = null
        guidePlayer = null
        handler.removeCallbacks(updateSeek)
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }

    override fun onScreenDestroyed() {
        pendingGuideStart?.let(binding.root::removeCallbacks)
        pendingGuideStart = null
        handler.removeCallbacks(updateSeek)
        guidePlayer?.release()
        guidePlayer = null
        if (!isRestoringDraft) {
            viewModel.saveDrawItems(binding.drawView.drawList)
        }
        viewModel.navScrollState = binding.rcvNav.layoutManager?.onSaveInstanceState()
        viewModel.partScrollState = binding.rcvPart.layoutManager?.onSaveInstanceState()
        isScaleActive = false
        super.onScreenDestroyed()
    }

    // ── INIT ──────────────────────────────────────────────────────────────────
    /** Trả về true nếu đã show dialog → caller nên block action */
    private fun checkOnlineNetworkOrShowDialog(): Boolean {
        if (isInternetAvailable(requireContext()) &&
            isNetworkConnected(requireContext())
        ) {
            isNoInternetDialogShowing = false
            return false
        }

        showNoInternetDialogOnce()
        return true
    }

    private fun showNoInternetDialogOnce() {
        if (!isAdded || isDetached || view == null || isNoInternetDialogShowing) return
        if (isNetworkConnected(requireContext())) return

        isNoInternetDialogShowing = true
        showOkDialog(
            title = getString(R.string.no_internet),
            message = getString(R.string.please_turn_on_internet_to_use_this_feature),
            onOk = { isNoInternetDialogShowing = false }
        )
    }

    override fun initView() {
        initNativeCollab()
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setFrameActionBar(btnActionBarRightText,tvRightText,getString(R.string.next ))

        }
        setupAdapters()
        binding.drawView.apply {
            setConstrained(true)
            setLocked(false)
            setOnDrawListener(object : OnDrawListener {
                override fun onAddedDraw(draw: Draw) {
                    (draw as? DrawableDraw)?.let(viewModel::addDraw)
                    updateSaveButtonState()
                }

                override fun onDeletedDraw(draw: Draw) {
                    (draw as? DrawableDraw)?.let {
                        if (viewModel.removeDraw(it)) {
                            adapterPart.clearSelection()
                        }
                    }
                    updateSaveButtonState()
                }

                override fun onClickedDraw(draw: Draw) = Unit
                override fun onDragFinishedDraw(draw: Draw) = Unit
                override fun onTouchedDownDraw(draw: Draw) {
                    (draw as? DrawableDraw)?.let {
                        shouldScrollNavToSelection = true
                        shouldScrollPartToSelection = true
                        viewModel.selectDraw(it)
                        updateAdapters(viewModel.state.value)
                    }
                }
                override fun onZoomFinishedDraw(draw: Draw) = Unit
                override fun onFlippedDraw(draw: Draw) = Unit
                override fun onDoubleTappedDraw(draw: Draw) = Unit
                override fun onHideOptionIconDraw() {
                    viewModel.clearCurrentDraw()
                }
                override fun onUndoDeleteDraw(draw: List<Draw?>) = Unit
                override fun onUndoUpdateDraw(draw: List<Draw?>) = Unit
                override fun onUndoDeleteAll() = Unit
                override fun onRedoAll() = Unit
                override fun onReplaceDraw(draw: Draw) = Unit
                override fun onEditText(draw: DrawableDraw) = Unit
                override fun onReplace(draw: Draw) = Unit
            })
            if (viewModel.drawItems.isEmpty()) {
                unSelectCurrentDraw()
                if (viewModel.draftItems.isNotEmpty()) {
                    restoreDraftItems()
                }
            } else {
                fillData(viewModel.drawItems)
                viewModel.currentDraw?.let { selectCurrentDraw(it) }
            }
        }
        updateSaveButtonState()

        scheduleInitialCatalogueLoad()
        if (viewModel.shouldShowInitialGuide()) {
            binding.bodyGuide.visibility = View.VISIBLE
            binding.win.visibility = View.VISIBLE
            guidePlaybackRequested = true
            autoGuidePlaybackPending = true
        } else {
            binding.win.visibility = View.GONE
            guidePlaybackRequested = false
            autoGuidePlaybackPending = false
        }
    }

    private fun restoreDraftItems() {
        if (isRestoringDraft) return
        isRestoringDraft = true
        val appContext = requireContext()
        val draft = if (isLowRamDevice) {
            viewModel.draftItems.take(
                if (isVeryLowRamDevice) {
                    VERY_LOW_RAM_MAX_LAYER_COUNT
                } else {
                    LOW_RAM_MAX_LAYER_COUNT
                }
            )
        } else {
            viewModel.draftItems
        }.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            val restored = withContext(Dispatchers.IO) {
                draft.mapNotNull { item ->
                    runCatching {
                        val target = Glide.with(appContext)
                            .asBitmap()
                            .load(item.path)
                            .disallowHardwareConfig()
                            .submit(canvasBitmapSizePx, canvasBitmapSizePx)
                        val bitmap = target.get()
                            .copy(Bitmap.Config.ARGB_8888, false)
                            ?: error("Cannot copy Studio bitmap")
                        target.cancel(true)
                        item to bitmap
                    }.getOrNull()
                }
            }
            if (!isAdded || isDetached || view == null || restored.isEmpty()) {
                isRestoringDraft = false
                return@launch
            }

            val draws = restored.mapTo(arrayListOf()) { (item, bitmap) ->
                DrawableDraw(bitmap.toDrawable(resources), item.path).apply {
                    setMatrix(Matrix().apply { setValues(item.matrixValues) })
                }
            }
            binding.drawView.fillData(draws)
            viewModel.saveDrawItems(draws)
            binding.drawView.unSelectCurrentDraw()
            updateSaveButtonState()
            isRestoringDraft = false
        }
    }

    private fun scheduleInitialCatalogueLoad() {
        hasTriggeredReInit = true
        viewModelActivity.loadStudioCatalogue()
    }

    private fun initializeGuidePlayer() {
        if (guidePlayer != null || !isAdded || view == null) return

        guidePlayer = ExoPlayer.Builder(requireContext()).build().also { player ->
            binding.playerViewGuide.player = player
            player.setMediaItem(
                MediaItem.fromUri(
                    "https://lvtglobal.site/public/app/ST038_EmojiMaker/video/video_instruction.mp4"
                )
            )
            if (guidePositionMs > 0L) {
                player.seekTo(guidePositionMs)
            }
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            binding.loadingGuide.visibility = View.VISIBLE
                        }

                        Player.STATE_READY -> {
                            guideLoadFailed = false
                            binding.loadingGuide.visibility = View.GONE
                            binding.seekGuide.max = player.duration.toInt()
                            handler.post(updateSeek)
                        }

                        Player.STATE_ENDED -> {
                            binding.btnPlayGuide.setImageResource(R.drawable.ic_play)
                            binding.btnPlayGuide.visibility = View.VISIBLE
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    guideLoadFailed = true
                    binding.loadingGuide.visibility = View.VISIBLE
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        binding.btnPlayGuide.visibility = View.GONE
                    } else {
                        binding.btnPlayGuide.setImageResource(R.drawable.ic_play)
                        binding.btnPlayGuide.visibility = View.VISIBLE
                    }
                }
            })
            player.prepare()
        }

        binding.seekGuide.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        guidePlayer?.seekTo(progress.toLong())
                        val duration = guidePlayer?.duration?.takeIf { it > 0 } ?: 0L
                        binding.tvGuideTime.text =
                            "${formatTime(progress.toLong())} / ${formatTime(duration)}"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isSeekingGuide = true
                    resumeGuideAfterSeek = guidePlayer?.isPlaying == true
                    guidePlayer?.pause()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress?.let { guidePlayer?.seekTo(it.toLong()) }
                    isSeekingGuide = false
                    if (resumeGuideAfterSeek) guidePlayer?.play()
                    resumeGuideAfterSeek = false
                }
            }
        )
    }

    private fun loadStudioCatalogue() {
        hasTriggeredReInit = true
        viewModelActivity.loadStudioCatalogue()
    }

    private fun toggleGuidePlayback() {
        if (guidePlayer == null) {
            initializeGuidePlayer()
        }
        guidePlayer?.let { player ->
            if (player.isPlaying) {
                guidePlaybackRequested = false
                player.pause()
            } else {
                guidePlaybackRequested = true
                if (guideLoadFailed) {
                    guideLoadFailed = false
                    player.prepare()
                }
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                player.play()
            }
        }
    }

    private fun showGuideAndPlay() {
        pendingGuideStart?.let(binding.root::removeCallbacks)
        pendingGuideStart = null
        autoGuidePlaybackPending = false
        binding.bodyGuide.visibility = View.VISIBLE
        binding.win.visibility = View.VISIBLE
        guidePlaybackRequested = true
        binding.win.post {
            if (!isAdded || isDetached || view == null) return@post
            initializeGuidePlayer()
            guidePlayer?.apply {
                if (guideLoadFailed) {
                    guideLoadFailed = false
                    prepare()
                }
                play()
            }
        }
    }

    private fun scheduleAutoGuidePlayback() {
        if (!autoGuidePlaybackPending || pendingGuideStart != null) return
        pendingGuideStart = Runnable {
            pendingGuideStart = null
            autoGuidePlaybackPending = false
            if (!isAdded || isDetached || view == null ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                binding.win.visibility != View.VISIBLE ||
                !guidePlaybackRequested
            ) {
                return@Runnable
            }
            initializeGuidePlayer()
            guidePlayer?.play()
        }.also { task ->
            binding.root.postDelayed(task, AUTO_GUIDE_START_DELAY_MS)
        }
    }

    private fun setupAdapters() {
        (binding.rcvPart.layoutManager as? GridLayoutManager)?.spanCount = MyApplication.editorGridSpanCount
        binding.rcvNav.setHasFixedSize(true)
        binding.rcvPart.setHasFixedSize(true)
        binding.rcvNav.itemAnimator = null
        binding.rcvPart.itemAnimator = null
        binding.rcvNav.adapter = adapterNav
        binding.rcvPart.adapter = adapterPart
    }

    // ── ACTIONS ───────────────────────────────────────────────────────────────
    override fun viewListener() {

        adapterNav.onClick = { navIndex ->
            if (!checkOnlineNetworkOrShowDialog()) {
                shouldScrollPartToSelection = true
                adapterNav.setPos(navIndex, binding.rcvNav)
                viewModel.selectNav(navIndex)
            }
        }
        adapterPart.onClick = { _, type ->
            if (!checkOnlineNetworkOrShowDialog()) {
                shouldScrollPartToSelection = false
                adapterPart.setSelectedPath(type)
                viewModel.selectPath(type)
                addDrawableToCanvas(type)
            }
        }

        binding.apply {
            btnGuide.onClick {
                if (checkOnlineNetworkOrShowDialog()) return@onClick
                showGuideAndPlay()
            }
            btnPlayGuide.onClick {
                toggleGuidePlayback()
            }
            playerViewGuide.onClick {
                toggleGuidePlayback()
            }
            close.onClick {
                pendingGuideStart?.let(root::removeCallbacks)
                pendingGuideStart = null
                autoGuidePlaybackPending = false
                guidePlaybackRequested = false
                guidePlayer?.pause()
                win.visibility = View.GONE
            }
            actionBar.btnActionBarRightText.onClick {
                if (!canSave) return@onClick
                if (!isAdded || isDetached || view == null) return@onClick
                if (checkOnlineNetworkOrShowDialog()) return@onClick
                if (canSave)
                        performSave()
            }
            actionBar.btnActionBarLeft.setOnClickListener { confirmExit() }
        }
    }



    private fun addDrawableToCanvas(path: String) {
        if (!isAdded || isDetached || view == null) return
        if (isLayerBitmapLoading) return
        if (!hasEnoughMemoryForLayer()) {
            adapterPart.clearSelection()
            showToast(getString(R.string.download_failed_please_try_again_later))
            return
        }
        isLayerBitmapLoading = true
        val requestManager = Glide.with(requireContext())
        requestManager
            .asBitmap()
            .load(path)
            .override(canvasBitmapSizePx, canvasBitmapSizePx)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .skipMemoryCache(true)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .disallowHardwareConfig()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    val ownedBitmap = try {
                        resource.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: OutOfMemoryError) {
                        null
                    }
                    if (ownedBitmap == null) {
                        isLayerBitmapLoading = false
                        adapterPart.clearSelection()
                        showToast(getString(R.string.download_failed_please_try_again_later))
                        // Glide không cho clear request ngay bên trong callback.
                        handler.post { requestManager.clear(this) }
                        return
                    }
                    try {
                        val draw = DrawableDraw(ownedBitmap.toDrawable(resources), path)
                        binding.drawView.addDraw(draw)
                    } catch (_: OutOfMemoryError) {
                        adapterPart.clearSelection()
                        showToast(getString(R.string.download_failed_please_try_again_later))
                    }
                    isLayerBitmapLoading = false
                    // Chờ onResourceReady kết thúc rồi mới trả bitmap gốc cho Glide.
                    handler.post { requestManager.clear(this) }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    isLayerBitmapLoading = false
                }
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    isLayerBitmapLoading = false
                    adapterPart.clearSelection()
                    showToast(getString(R.string.download_failed_please_try_again_later))
                }
            })
    }

    // ── OBSERVE ───────────────────────────────────────────────────────────────
    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModelActivity.studioCatalogue.collectLatest { parts ->
                        if (parts.isNotEmpty()) {
                            viewModel.setCatalogue(parts)
                        }
                    }
                }
                launch {
                    viewModel.state.collectLatest { state ->
                        state.error?.let {
                            showToast(getString(R.string.download_failed_please_try_again_later))
                            viewModel.consumeError()
                            return@collectLatest
                        }
                        if (state.listData.isEmpty()) {
                            if (!hasTriggeredReInit) {
                                hasTriggeredReInit = true
                                loadStudioCatalogue()
                            }
                            return@collectLatest
                        }
                        hasTriggeredReInit = false  // reset khi state đã có data

                        updateAdapters(state)
                    }
                }
                launch {
                    viewModelActivity.networkOnline.collectLatest { isOnline ->
                        val wasOnline = lastNetworkOnline
                        lastNetworkOnline = isOnline
                        if (isOnline) {
                            isNoInternetDialogShowing = false
                            if (guideLoadFailed && guidePlaybackRequested) {
                                guideLoadFailed = false
                                guidePlayer?.apply {
                                    prepare()
                                    play()
                                }
                            }
                        } else if (
                            wasOnline == true &&
                            !isNetworkConnected(requireContext()) &&
                            binding.win.visibility == View.VISIBLE
                        ) {
                            showToast(R.string.no_internet)
                        }
                    }
                }
            }
        }
    }





    // ✅ Thêm vào onResume: reset pendingLoads khi quay lại
    override fun onResume() {
        super.onResume()
        scheduleAutoGuidePlayback()

        val state = viewModel.state.value
        if (state.listData.isEmpty()) {
            if (!hasTriggeredReInit) {
                hasTriggeredReInit = true
                loadStudioCatalogue()
            }
            return
        }
        // ✅ Nếu state empty thì để observeData xử lý re-init

        updateAdapters(state)
    }

    private fun setSaveEnabled(enabled: Boolean) {
        canSave = enabled
        binding.actionBar.btnActionBarRightText.alpha = if (enabled) 1f else 0.5f
        binding.actionBar.btnActionBarRightText.isEnabled = enabled
    }

    private fun updateSaveButtonState() {
        setSaveEnabled(viewModel.drawItems.isNotEmpty())
    }

    // ── ADAPTERS ──────────────────────────────────────────────────────────────

    private fun updateAdapters(state: CatStudioState) {
        val navChanged = lastNavIndex != state.currentNavIndex
        if (navChanged) lastNavIndex = state.currentNavIndex

        adapterNav.setPos(state.currentNavIndex, binding.rcvNav)
        val catalogueChanged = lastNavData !== state.listData
        if (catalogueChanged) {
            lastNavData = state.listData
            adapterNav.submitList(state.listData)
        }

        // Khởi tạo arrShowColor khi data load lần đầu
        if (arrShowColor.size != state.listData.size) {
            arrShowColor.clear()
            repeat(state.listData.size) { arrShowColor.add(true) }
        }

        // ── Part ───────────────────────────────────────────────────────────────
        if (navChanged || catalogueChanged) {
            val bp = state.listData.getOrNull(state.currentNavIndex)
            adapterPart.listThumb = buildThumbList(bp, state.currentPaths)
            adapterPart.submitList(state.currentPaths)
        }
        adapterPart.setSelectedPath(state.selectedPath)
        if (shouldScrollNavToSelection) {
            binding.rcvNav.scrollToPosition(state.currentNavIndex)
            shouldScrollNavToSelection = false
        }
        if (shouldScrollPartToSelection || navChanged) {
            val selectedIndex = state.selectedPath
                ?.let(state.currentPaths::indexOf)
                ?.takeIf { it >= 0 }
                ?: 0
            binding.rcvPart.scrollToPosition(selectedIndex)
        }
        shouldScrollPartToSelection = false

        binding.rcvNav.post {
            viewModel.navScrollState?.let {
                binding.rcvNav.layoutManager?.onRestoreInstanceState(it)
                viewModel.navScrollState = null
            }
        }
        binding.rcvPart.post {
            viewModel.partScrollState?.let {
                binding.rcvPart.layoutManager?.onRestoreInstanceState(it)
                viewModel.partScrollState = null
            }
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
            // Android Views must be read and drawn on the main thread.
            val bitmap = renderLayersToBitmap()
            if (bitmap == null) {
                updateSaveButtonState()
                hideLoadingSafe()
                return@launch
            }
            viewModelActivity.customizeBitmap = bitmap
            val savedPath = withContext(Dispatchers.IO) {
                bitmap.saveToFile(requireActivity(), "avatar")
            }

            if (savedPath == null) {
                updateSaveButtonState()
                hideLoadingSafe()
                return@launch
            }

            // ✅ Navigate trực tiếp trên Main thread, KHÔNG wrap thêm withContext
            if (isAdded && !isDetached) {
                hideLoadingSafe()
                updateSaveButtonState()
                activityRouter().navigate(
                    R.id.action_customize_to_addCharacter,
                    Bundle().apply {
                        putString("imagePath", savedPath)
                        putString(DesignSource.ARG_SOURCE, DesignSource.STUDIO)
                    }
                )
            }
        }
    }

    private fun renderLayersToBitmap(): Bitmap? {
        if (binding.drawView.width == 0 || binding.drawView.height == 0) return null
        return binding.drawView.save()
    }

    // ── BASE OVERRIDES ────────────────────────────────────────────────────────

    override fun bindViewModel() {}

    //--------------------------------Backpress
    override fun onScreenBackPressed(): Boolean {
        if (binding.win.visibility == View.VISIBLE) {
            pendingGuideStart?.let(binding.root::removeCallbacks)
            pendingGuideStart = null
            autoGuidePlaybackPending = false
            guidePlaybackRequested = false
            guidePlayer?.pause()
            binding.win.visibility = View.GONE
            return true
        }
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
                    activityRouter().navigateUp()
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
        private const val AUTO_GUIDE_START_DELAY_MS = 220L
        private const val CANVAS_BITMAP_SIZE_PX = 512
        private const val LOW_RAM_CANVAS_BITMAP_SIZE_PX = 320
        private const val VERY_LOW_RAM_CANVAS_BITMAP_SIZE_PX = 256
        private const val LOW_RAM_MAX_LAYER_COUNT = 20
        private const val VERY_LOW_RAM_MAX_LAYER_COUNT = 12
        private const val LOW_RAM_HEAP_CLASS_MB = 192
        private const val VERY_LOW_RAM_HEAP_CLASS_MB = 128
        private const val LOW_RAM_TOTAL_MEMORY_BYTES = 3L * 1024L * 1024L * 1024L
        private const val VERY_LOW_RAM_TOTAL_MEMORY_BYTES = 2L * 1024L * 1024L * 1024L
        private const val ARGB_8888_BYTES_PER_PIXEL = 4L
        private const val BITMAP_COPIES_WHILE_ADDING = 2L
        private const val MIN_HEAP_RESERVE_BYTES = 6L * 1024L * 1024L
    }
}
