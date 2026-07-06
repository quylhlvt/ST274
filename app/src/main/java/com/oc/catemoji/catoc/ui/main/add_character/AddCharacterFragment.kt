package com.oc.catemoji.catoc.ui.main.add_character

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.custom.Draw
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.core.custom.listener.listenerdraw.OnDrawListener
import com.oc.catemoji.catoc.core.dialog.ChooseColorDialog
import com.oc.catemoji.catoc.core.dialog.DialogSpeech
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.checkPermissions
import com.oc.catemoji.catoc.core.extention.dp
import com.oc.catemoji.catoc.core.extention.dpToPx
import com.oc.catemoji.catoc.core.extention.drawToBitmap
import com.oc.catemoji.catoc.core.extention.goToSettings
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.core.extention.hideSoftKeyboard
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setFont
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.BitmapHelper
import com.oc.catemoji.catoc.databinding.FragmentAddCharacterBinding
import com.oc.catemoji.catoc.ui.main.add_character.adapter.BackgroundColorAdapter
import com.oc.catemoji.catoc.ui.main.add_character.adapter.BackgroundImageAdapter
import com.oc.catemoji.catoc.ui.main.add_character.adapter.StickerAdapter
import com.oc.catemoji.catoc.ui.main.add_character.adapter.TextColorAdapter
import com.oc.catemoji.catoc.ui.main.add_character.adapter.TextFontAdapter
import com.oc.catemoji.catoc.ui.onboarding.permission.PermissionViewModel
import com.oc.catemoji.catoc.utils.DataLocal
import com.oc.catemoji.catoc.utils.key.ValueKey
import com.oc.catemoji.catoc.data.datalocal.manager.CharacterImageManager
import com.oc.catemoji.catoc.ui.main.add_character.adapter.SpeechAdapter
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AddCharacterFragment : BaseFragment<FragmentAddCharacterBinding, AddCharacterViewModel>(
    FragmentAddCharacterBinding::inflate,
    AddCharacterViewModel::class.java
), BackPressHandler {
    @Inject
    lateinit var imageManager: CharacterImageManager
    private val permissionViewModel: PermissionViewModel by viewModels()
    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    // ── Keyboard state ──────────────────────────────────────────────────────
    // Source of truth duy nhất: layout change listener đo thực tế
    // KHÔNG dùng boolean flag nào trong ViewModel để control layout
    private var isKeyboardOpen = false

    // ── Adapters ─────────────────────────────────────────────────────────────
    private val backgroundImageAdapter by lazy { BackgroundImageAdapter() }
    private val backgroundColorAdapter by lazy { BackgroundColorAdapter() }
    private val stickerAdapter by lazy { StickerAdapter() }
    private val speechAdapter by lazy { SpeechAdapter() }
    private val textFontAdapter by lazy { TextFontAdapter(requireContext()) }
    private val textColorAdapter by lazy { TextColorAdapter() }

    private val imagepath: String by lazy {
        arguments?.getString("imagePath") ?: ""
    }

    private fun buttonNavigationList() = arrayListOf(
        binding.btnBackground,
        binding.btnSticker,
        binding.btnSpeech,
        binding.btnText,
    )

    private fun layoutNavigationList() = arrayListOf(
        binding.lnlBackground.root,
        binding.lnlSticker,
        binding.lnlSpeech,
        binding.lnlText.scvText,
    )

    // ── Launchers ─────────────────────────────────────────────────────────────
    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            viewModel.isPickingImage = false
            binding.flNativeCollab.visible()
            hideLoadingSafe()

            if (uri != null) {
                runCatching {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                handleSetBackgroundImage(uri.toString(), -1)
            } else {
                restoreUIState()
            }
        }

    // ── Inflate ───────────────────────────────────────────────────────────────
    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentAddCharacterBinding = FragmentAddCharacterBinding.inflate(inflater, container, false)

    private fun initNativeCollab() {
        Admob.getInstance().loadNativeCollapNotBanner(
            requireContext(),
            getString(R.string.native_cl_bg),
            binding.flNativeCollab
        )
    }

    override fun onFragmentStart() {
        if (!isAdded || isDetached) return
        if (viewModel.isPickingImage) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        initNativeCollab()
    }

    override fun onFragmentStop() {
        if (!isAdded || isDetached) return
        if (viewModel.isPickingImage) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }

    // ── Observe ───────────────────────────────────────────────────────────────
    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.typeNavigation.collect { type ->
                        if (type != -1) setupTypeNavigation(type)
                        requireActivity().hideNavigation(true)
                    }
                }
                launch {
                    viewModel.typeBackground.collect { type ->
                        if (type != -1) setupTypeBackground(type)
                        requireActivity().hideNavigation(true)
                    }
                }
                launch {
                    viewModel.backgroundImagePath.collect { path ->
                        path?.let { loadImage(requireContext(), it, binding.imvBackground) }
                    }
                }
                launch {
                    viewModelActivity.bgStickerReady.collect { ready ->
                        if (!ready || !viewModel.isInitialized) return@collect
                        Log.d("DEBUG_STICKER", "bgStickerReady=$ready, isInitialized=${viewModel.isInitialized}, hasLoadedData=${viewModel.hasLoadedData}, stickerSize=${viewModel.stickerList.size}")
                        // ✅ Chỉ load lại nếu chưa có data
                        if (!viewModel.hasLoadedData) {
                            viewModel.loadDataFromQuantity(
                                bgQuantity = viewModelActivity.bgQuantity,
                                stickerQuantity = viewModelActivity.stickerQuantity,
                                bgBaseUrl = viewModelActivity.bgBaseUrl,
                                speeches = viewModelActivity.speechs.value
                            )
                        }
                        Log.d("DEBUG_STICKER", "after submit stickerSize=${viewModel.stickerList.size}")
                        backgroundImageAdapter.submitList(viewModel.backgroundImageList)
                        stickerAdapter.submitList(viewModel.stickerList, true)
                        speechAdapter.submitList(viewModel.speechList)
                    }
                }
                launch {
                    viewModelActivity.backgrounds.collectLatest { bgs ->
                        if (bgs.isNotEmpty()) {
                            // ✅ Chỉ load lại nếu chưa có data
                            if (!viewModel.hasLoadedData) {
                                viewModel.loadDataFromMainViewModel(
                                    bgs,
                                    viewModelActivity.stickers.value,
                                    viewModelActivity.speechs.value
                                )
                            }
                            backgroundImageAdapter.submitList(viewModel.backgroundImageList)
                            stickerAdapter.submitList(viewModel.stickerList, true)
                        }
                    }
                }
            }
        }
    }

    override fun bindViewModel() {}
    private fun checkOnlineNetworkOrShowDialog(): Boolean {

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

    // ── Listeners ─────────────────────────────────────────────────────────────
    override fun viewListener() {
        binding.apply {
            // Action bar
            actionBar.btnActionBarLeft.onClick { confirmExit() }
            actionBar.btnActionBarCenter1.onClick { confirmReset() }
            actionBar.btnActionBarRight.onClick {
                showInter {
                    handleSave()
                }
            }

            // Background tabs
            lnlBackground.btnBackgroundImage.onClick {
                viewModel.setTypeBackground(ValueKey.IMAGE_BACKGROUND)
            }
            lnlBackground.btnBackgroundColor.onClick {
                viewModel.setTypeBackground(ValueKey.COLOR_BACKGROUND)
            }

            // Bottom navigation
            btnBackground.onClick {
                clearFocus()
                viewModel.isTextTabActive = false
                viewModel.setTypeNavigation(ValueKey.BACKGROUND_NAVIGATION)
            }
            btnSticker.onClick {
                clearFocus()
                viewModel.isTextTabActive = false
                viewModel.setTypeNavigation(ValueKey.STICKER_NAVIGATION)
            }
            btnSpeech.onClick {
                clearFocus()
                viewModel.isTextTabActive = false
                viewModel.setTypeNavigation(ValueKey.SPEECH_NAVIGATION)
            }
            btnText.onClick {
                viewModel.isTextTabActive = true
                viewModel.setTypeNavigation(ValueKey.TEXT_NAVIGATION)
            }

            // EditText
            lnlText.edtText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    tvGetText.text = s.toString()
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            lnlText.edtText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    clearFocus()
                    true
                } else false
            }

            lnlText.btnDoneText.onClick {
                handleDoneText()
                clearFocus(true)
            }

            // Click ngoài → đóng keyboard
            main.onClick { clearFocus() }

            // Adapters
            backgroundImageAdapter.onAddImageClick = { launchImagePicker() }
            backgroundImageAdapter.onBackgroundImageClick = { path, position ->
                if (!checkOnlineNetworkOrShowDialog()) {
                    handleSetBackgroundImage(path, position)
                }
            }
            backgroundColorAdapter.onChooseColorClick = { handleChooseColor() }
            backgroundColorAdapter.onBackgroundColorClick = { color, position ->
                handleSetBackgroundColor(color, position)
            }
            stickerAdapter.onItemClick = { path ->
                if (!checkOnlineNetworkOrShowDialog()) {
                    stickerAdapter.selectItem(stickerAdapter.items.indexOfFirst { it.path == path })
                    addDrawable(path)
                }
            }
            speechAdapter.onItemClick = { path -> handleSpeech(path) }
            textFontAdapter.onTextFontClick = { font, position -> handleFontClick(font, position) }
            textColorAdapter.onChooseColorClick = { handleChooseColor(isTextColor = true) }
            textColorAdapter.onTextColorClick = { color, position ->
                handleTextColorClick(color, position)
            }
        }

        initActionBar()
        requireActivity().hideNavigation(true)
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    override fun initView() {

        initNativeCollab()
        binding.lnlBackground.btnBackgroundColorTv.isSelected = true
        binding.lnlBackground.btnBackgroundImageTv.isSelected = true
        requireActivity().hideNavigation(true)
        setupKeyboardListener()
        binding.tvGetText.setTextColor(requireContext().getColor(R.color.black))
        initRcv()
        initDrawView()

        if (!viewModel.isInitialized) {
            initData()
            viewModel.isInitialized = true
        } else {
            hideLoadingSafe()
            restoreUIState()
        }

    }

    // ── Keyboard ─────────────────────────────────────────────────────a─────────

    /**
     * Source of truth duy nhất cho keyboard state và flFunction position.
     *
     * Logic:
     * - Keyboard lên (heightDiff > THRESHOLD):
     *     → Tab Text + speech dialog không mở → set bottomMargin = -170dp (cố định)
     *     → Các tab khác hoặc speech dialog đang mở → giữ nguyên (margin = 0)
     * - Keyboard xuống (heightDiff < -THRESHOLD):
     *     → Luôn reset margin = 0, bất kể tab nào
     */
    private fun setupKeyboardListener() {
        // Android 10+ dùng WindowInsets
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                if (imeVisible && imeHeight > 0) onKeyboardOpen()
                else onKeyboardClose()
                insets
            }
        } else {
            // Android 9 trở xuống: dùng GlobalLayout
            setupKeyboardListenerLegacy()
        }
    }

    private fun setupKeyboardListenerLegacy() {
        val threshold = 150.dp(requireContext())

        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > threshold) {
                onKeyboardOpen()
            } else {
                onKeyboardClose()
            }
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
    }

    private fun onKeyboardOpen() {
        isKeyboardOpen = true
        if (viewModel.isTextTabActive && !viewModel.isSpeechDialogOpen) {
            binding.flFunction.translationY = (-170).dp(requireContext()).toFloat()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        keyboardLayoutListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        keyboardLayoutListener = null
    }

    private fun onKeyboardClose() {
        // ✅ Android 9-: ignore nếu speech dialog đang mở
        // vì GlobalLayoutListener fire false-close khi dialog transition
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q
            && viewModel.isSpeechDialogOpen
        ) return

        isKeyboardOpen = false
        binding.flFunction.translationY = 0f
    }

    // ĐỔI TÊN + ĐỔI bottomMargin → topMargin
    private fun setFlFunctionTopMargin(margin: Int) {
        (binding.flFunction.layoutParams as ViewGroup.MarginLayoutParams).topMargin = margin
    }

    /**
     * Đóng keyboard và reset view.
     * Dùng ở mọi nơi cần dismiss keyboard — backpress, click ngoài, done text, tab switch.
     */
    private fun collapseKeyboard() {
        binding.lnlText.edtText.clearFocus()
        binding.drawView.hideSelect()
        hideSoftKeyboard()
        // Reset view ngay lập tức, không đợi layout change
        setFlFunctionTopMargin(0)
    }

    private fun clearFocus(check: Boolean = false) {
        if (!check)
            binding.drawView.hideSelect()
        hideSoftKeyboard()
        setFlFunctionTopMargin(0)
        lifecycleScope.launch {
            delay(50)
            binding.lnlText.edtText.clearFocus()
        }
    }

    // ── Data ──────────────────────────────────────────────────────────────────
    private fun initActionBar() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setImageActionBar(btnActionBarCenter1, R.drawable.ic_reset_all_custom)
            setImageActionBar(btnActionBarRight, R.drawable.next_app)
        }
    }

    private fun initRcv() {
        binding.apply {
            lnlBackground.rcvBackgroundImage.apply {
                adapter = backgroundImageAdapter; itemAnimator = null
                setHasFixedSize(true); setItemViewCacheSize(10)
            }
            lnlBackground.rcvBackgroundColor.apply {
                adapter = backgroundColorAdapter; itemAnimator = null
            }
            rcvSticker.apply {
                adapter = stickerAdapter; itemAnimator = null
                setHasFixedSize(true); setItemViewCacheSize(10)
            }
            rcvSpeech.apply {
                adapter = speechAdapter; itemAnimator = null
                setHasFixedSize(true); setItemViewCacheSize(10)
            }
            lnlText.rcvFont.apply { adapter = textFontAdapter; itemAnimator = null }
            lnlText.rcvTextColor.apply { adapter = textColorAdapter; itemAnimator = null }
        }
        requireActivity().hideNavigation(true)
    }

    private fun initData() {
        // ✅ Luôn load từ số lượng, kể cả offline (dùng số lượng đã cache trong ViewModel)
        viewModel.loadDataFromQuantity(
            bgQuantity = viewModelActivity.bgQuantity,
            stickerQuantity = viewModelActivity.stickerQuantity,
            bgBaseUrl = viewModelActivity.bgBaseUrl,
            speeches = viewModelActivity.speechs.value
        )
        submitAllAdapters()
        viewModel.setTypeNavigation(ValueKey.BACKGROUND_NAVIGATION)
        viewModel.setTypeBackground(ValueKey.IMAGE_BACKGROUND)

        viewLifecycleOwner.lifecycleScope.launch {
            val customizeBitmap = viewModelActivity.customizeBitmap
            if (customizeBitmap != null && !customizeBitmap.isRecycled) {
                binding.drawView.addDraw(
                    viewModel.loadDrawableEmoji(customizeBitmap, isCharacter = true)
                )
                hideLoadingSafe()
            } else if (imagepath.isNotEmpty()) {
                addDrawable(imagepath, isCharacter = true) { hideLoadingSafe() }
            } else {
                hideLoadingSafe()
            }
        }
    }

    private fun submitAllAdapters() {
        backgroundImageAdapter.submitList(viewModel.backgroundImageList)
        backgroundColorAdapter.submitList(viewModel.backgroundColorList, true)
        stickerAdapter.submitList(viewModel.stickerList, true)
        speechAdapter.submitList(viewModel.speechList)
        textFontAdapter.submitListReset(viewModel.textFontList)
        textColorAdapter.submitListReset(viewModel.textColorList)
    }

    private fun restoreUIState() {
        Log.d("DEBUG_STICKER", "restoreUIState stickerSize=${viewModel.stickerList.size}, hasLoadedData=${viewModel.hasLoadedData}")

        // ✅ Chỉ submit adapter nếu có data, không submit list rỗng
        if (viewModel.stickerList.isNotEmpty()) {
            submitAllAdapters()
        } else {
            // Submit các list khác không phụ thuộc network
            backgroundColorAdapter.submitList(viewModel.backgroundColorList, true)
            textFontAdapter.submitListReset(viewModel.textFontList)
            textColorAdapter.submitListReset(viewModel.textColorList)
        }

        val currentNav = viewModel.typeNavigation.value
        if (currentNav != -1) setupTypeNavigation(currentNav)

        val currentBg = viewModel.typeBackground.value
        if (currentBg != -1) setupTypeBackground(currentBg)

        val imagePath = viewModel.backgroundImagePath.value
        val savedColor = viewModel.savedBackgroundColor
        when {
            imagePath != null -> {
                binding.imvBackground.setBackgroundColor(requireContext().getColor(R.color.transparent))
                loadImage(requireContext(), imagePath, binding.imvBackground)
            }
            savedColor != null -> {
                binding.imvBackground.setImageBitmap(null)
                binding.imvBackground.setBackgroundColor(savedColor)
            }
        }

        if (viewModel.drawViewList.isNotEmpty()) {
            viewModel.isRestoringDraws = true
            binding.drawView.fillData(viewModel.drawViewList)
            viewModel.isRestoringDraws = false
        }
    }

    // ── DrawView ──────────────────────────────────────────────────────────────
    private fun initDrawView() {
        requireActivity().hideNavigation(true)
        binding.drawView.apply {
            setConstrained(true)
            setLocked(false)
            setOnDrawListener(object : OnDrawListener {
                override fun onAddedDraw(draw: Draw) {
                    if (!viewModel.isRestoringDraws) {
                        viewModel.updateCurrentCurrentDraw(draw)
                        viewModel.addDrawView(draw)
                    }
                }

                override fun onClickedDraw(draw: Draw) {}
                override fun onDeletedDraw(draw: Draw) {
                    viewModel.deleteDrawView(draw)
                }

                override fun onDragFinishedDraw(draw: Draw) {}
                override fun onTouchedDownDraw(draw: Draw) {
                    viewModel.updateCurrentCurrentDraw(draw)
                }

                override fun onZoomFinishedDraw(draw: Draw) {}
                override fun onFlippedDraw(draw: Draw) {}
                override fun onDoubleTappedDraw(draw: Draw) {}
                override fun onHideOptionIconDraw() {}
                override fun onUndoDeleteDraw(draw: List<Draw?>) {}
                override fun onUndoUpdateDraw(draw: List<Draw?>) {}
                override fun onUndoDeleteAll() {}
                override fun onRedoAll() {}
                override fun onReplaceDraw(draw: Draw) {}
                override fun onEditText(draw: DrawableDraw) {}
                override fun onReplace(draw: Draw) {}
            })
        }
    }

    private fun addDrawable(
        path: String,
        isCharacter: Boolean = false,
        bitmapText: Bitmap? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (bitmapText != null) {
            binding.drawView.addDraw(viewModel.loadDrawableEmoji(bitmapText, isCharacter))
            onDone?.invoke()
            return
        }
        Glide.with(this)
            .asBitmap()
            .load(path)
            .override(512, 512)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
            .disallowHardwareConfig()
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                ) {
                    binding.drawView.addDraw(viewModel.loadDrawableEmoji(resource, isCharacter))
                    requireActivity().hideNavigation(true)
                    onDone?.invoke()
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    showToast("Don't Download sticker")
                    onDone?.invoke()
                }
            })
    }

    // ── UI setup ──────────────────────────────────────────────────────────────
    private fun setupTypeBackground(type: Int) {
        binding.apply {
            when (type) {
                ValueKey.IMAGE_BACKGROUND -> {
                    requireActivity().hideNavigation(true)
                    lnlBackground.rcvBackgroundImage.visible()
                    lnlBackground.rcvBackgroundColor.gone()
                    lnlBackground.btnBackgroundImage.setBackgroundResource(R.drawable.img_bg_image_addcharacter)
                    lnlBackground.btnBackgroundColor.setBackgroundResource(R.drawable.img_bg_color_addcharacter)
                    backgroundImageAdapter.submitList(viewModel.backgroundImageList)
                }

                ValueKey.COLOR_BACKGROUND -> {
                    requireActivity().hideNavigation(true)
                    lnlBackground.rcvBackgroundImage.gone()
                    lnlBackground.rcvBackgroundColor.visible()
                    lnlBackground.btnBackgroundColor.setBackgroundResource(R.drawable.img_bg_image_addcharacter)
                    lnlBackground.btnBackgroundImage.setBackgroundResource(R.drawable.img_bg_color_addcharacter)
                    backgroundColorAdapter.submitList(viewModel.backgroundColorList)
                }
            }
        }
    }

    private fun setupTypeNavigation(type: Int) {
        buttonNavigationList().forEachIndexed { index, button ->
            val (res, status) = if (index == type) {
                DataLocal.bottomNavigationSelected[index] to true
            } else {
                DataLocal.bottomNavigationNotSelect[index] to false
            }
            button.setImageResource(res)
            layoutNavigationList()[index].isVisible = status
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────
    private fun confirmExit() {
        clearFocus()
        showConfirmDialog(
            message = getString(R.string.haven_t_saved_it_yet_do_you_want_to_exit),
            title = getString(R.string.exit),
            onYes = { hideLoadingSafe(); findNavController().navigateUp() },
            onNo = { hideLoadingSafe() }
        )
    }

    private fun confirmReset() {
        clearFocus()
        showConfirmDialog(
            message = getString(R.string.do_you_want_to_reset_all),
            title = getString(R.string.reset),
            onYes = {
                Log.d("DEBUG_STICKER", "RESET called")
                viewModel.hasLoadedData = false
                showLoadingSafe()

                // ✅ Dùng loadDataFromQuantity thay vì loadDataFromMainViewModel
                // vì backgrounds.value có thể rỗng
                viewModel.loadDataFromQuantity(
                    bgQuantity = viewModelActivity.bgQuantity,
                    stickerQuantity = viewModelActivity.stickerQuantity,
                    bgBaseUrl = viewModelActivity.bgBaseUrl,
                    speeches = viewModelActivity.speechs.value
                )

                submitAllAdapters()  // ✅ Submit ngay sau khi có data

                viewModel.resetDraw()
                viewModel.setBackgroundImage(null)
                viewModel.savedBackgroundColor = null
                binding.drawView.removeAllDraw()
                binding.imvBackground.setImageBitmap(null)
                binding.imvBackground.setBackgroundColor(requireContext().getColor(R.color.transparent))
                backgroundImageAdapter.clearSelection()
                backgroundColorAdapter.clearSelection()
                hideLoadingSafe()

                val cachedBitmap = viewModelActivity.customizeBitmap
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                    binding.drawView.addDraw(viewModel.loadDrawableEmoji(cachedBitmap, isCharacter = true))
                    hideLoadingSafe()
                } else if (imagepath.isNotEmpty()) {
                    addDrawable(imagepath, isCharacter = true)
                }
            },
            onNo = { hideLoadingSafe() }
        )
    }

    private fun handleSetBackgroundImage(path: String, position: Int) {
        viewModel.setBackgroundImage(path)
        viewModel.savedBackgroundColor = null
        binding.imvBackground.setBackgroundColor(requireContext().getColor(R.color.transparent))
        loadImage(requireContext(), path, binding.imvBackground)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            viewModel.updateBackgroundImageSelected(position)
            withContext(Dispatchers.Main) {
                backgroundColorAdapter.clearSelection()
                backgroundImageAdapter.selectItem(position)
            }
        }
    }

    private fun handleSetBackgroundColor(color: Int, position: Int) {
        binding.imvBackground.setImageBitmap(null)
        binding.imvBackground.setBackgroundColor(color)
        viewModel.savedBackgroundColor = color
        viewModel.setBackgroundImage(null)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            viewModel.updateBackgroundColorSelected(position)
            withContext(Dispatchers.Main) {
                backgroundImageAdapter.clearSelection()
                backgroundColorAdapter.selectItem(position)
            }
        }
    }


    private fun launchImagePicker() {
        viewModel.isPickingImage = true
        showLoadingSafe()

        binding.root.post {
            if (!isAdded || isDetached) {
                viewModel.isPickingImage = false
                hideLoadingSafe()
                return@post
            }

            runCatching {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }.onFailure {
                viewModel.isPickingImage = false
                hideLoadingSafe()
                restoreUIState()
            }
        }
    }

    private fun handleChooseColor(isTextColor: Boolean = false) {
        val dialog = ChooseColorDialog(requireContext())
        dialog.show()
        dialog.onCloseEvent = { dialog.dismiss() }
        dialog.onDoneEvent = { color ->
            dialog.dismiss()
            if (!isTextColor) handleSetBackgroundColor(color, 0)
            else handleTextColorClick(color, 0)
        }
    }

    /**
     * Speech dialog có EditText riêng với keyboard riêng.
     * flFunction KHÔNG được đẩy lên khi keyboard của dialog mở.
     *
     * Giải pháp: set isSpeechDialogOpen = true TRƯỚC KHI dialog show.
     * Layout change listener sẽ check flag này và bỏ qua keyboard event.
     */
    private fun handleSpeech(path: String) {
        viewModel.isSpeechDialogOpen = true
        binding.lnlText.edtText.clearFocus()
        hideSoftKeyboard()

        val dialog = DialogSpeech(requireContext(), path)

        // ✅ Android 9-: SOFT_INPUT_STATE_VISIBLE để keyboard tự hiện
        // BaseDialog đã set ADJUST_RESIZE, chỉ cần OR thêm state
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
        }

        dialog.show()

        dialog.onDoneClick = { bitmap ->
            dialog.dismiss()
            requireActivity().hideNavigation(true)
            if (bitmap != null) addDrawable("", bitmapText = bitmap)
        }

        dialog.setOnDismissListener {
            viewModel.isSpeechDialogOpen = false
            setFlFunctionTopMargin(0)
            requireActivity().hideNavigation(true)
        }
    }

    private fun handleFontClick(font: Int, position: Int) {
        binding.lnlText.edtText.setFont(font)
        binding.tvGetText.setFont(font)
        viewModel.updateTextFontSelected(position)
        textFontAdapter.submitItem(position, viewModel.textFontList)
    }

    private fun handleTextColorClick(color: Int, position: Int) {
        binding.lnlText.edtText.setTextColor(color)
        binding.tvGetText.setTextColor(color)
        viewModel.updateTextColorSelected(position)
        textColorAdapter.submitItem(position, viewModel.textColorList)
    }

    @SuppressLint("SimpleDateFormat")
    private fun handleDoneText() {
        clearFocus()
        binding.apply {
            val text = lnlText.edtText.text.toString().trim()
            if (text.isEmpty()) {
                showToast(getString(R.string.null_edt))
                return
            }
            tvGetText.text = text
            val bitmap = BitmapHelper.getBitmapFromEditText(tvGetText)
            drawView.addDraw(viewModel.loadDrawableEmoji(bitmap, isText = true))

            // Reset text tab
            val font = viewModel.textFontList.first().color
            val color = viewModel.textColorList[1].color
            lnlText.edtText.text = null
            lnlText.edtText.setFont(font)
            lnlText.edtText.setTextColor(color)
            viewModel.updateTextFontSelected(0)
            viewModel.updateTextColorSelected(1)
            textFontAdapter.submitListReset(viewModel.textFontList)
            textColorAdapter.submitListReset(viewModel.textColorList)
            tvGetText.text = ""
            tvGetText.setFont(font)
            tvGetText.setTextColor(color)
        }
    }

    private fun handleSave() {
        clearFocus()
        viewLifecycleOwner.lifecycleScope.launch {
            showLoadingSafe()
            try {
                val bitmap = binding.flSave.drawToBitmap()
                val timestamp = SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(java.util.Date())
                val designId = "design_$timestamp"

                val savedImagePath = withContext(Dispatchers.IO) {
                    imageManager.deleteOldImage(designId)
                    val path = imageManager.saveBitmap(bitmap, designId)
                    if (path != null) viewModelActivity.appDataManager.addMyDesignPath(path)
                    path
                }
                hideLoadingSafe()

                if (savedImagePath != null) {
                    findNavController().navigate(
                        R.id.action_addCharacterFragment_to_viewImageFragment,
                        Bundle().apply {
                            putString("imagePath", savedImagePath)
                            putString("idEdit", "")
                            putInt("imageType", 0)
                        }
                    )
                } else {
                    Toast.makeText(requireContext(), "Lưu thất bại!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                hideLoadingSafe()
                Toast.makeText(requireContext(), "Có lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Back press ────────────────────────────────────────────────────────────
    /**
     * Logic backpress:
     * - Keyboard đang mở → đóng keyboard, KHÔNG back
     * - Keyboard đóng → hiện confirm dialog
     */
    override fun onBackPressed(): Boolean {
        return if (isKeyboardOpen) {
            collapseKeyboard()
            true // consumed
        } else {
            confirmExit()
            true // consumed
        }
    }
}
