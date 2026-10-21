package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.EditText
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.oc.catemoji.catoc.DiyAssetCategory
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.custom.Draw
import com.oc.catemoji.catoc.core.custom.DrawKey
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.core.custom.listener.listenerdraw.OnDrawListener
import com.oc.catemoji.catoc.core.dialog.ChooseColorDialog
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.customDiy.DiyCatalogue
import com.oc.catemoji.catoc.databinding.ActivityCustomDiyPetBinding
import com.oc.catemoji.catoc.ui.main.add_character.AddCharacterViewModel
import com.oc.catemoji.catoc.ui.main.add_character.DesignSource
import com.oc.catemoji.catoc.ui.main.add_character.adapter.BackgroundColorAdapter
import com.oc.catemoji.catoc.ui.main.add_character.adapter.BackgroundImageAdapter
import com.oc.catemoji.catoc.ui.main.view.ViewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CustomDiyPetActivity : BaseActivity<ActivityCustomDiyPetBinding, CustomDiyPetViewModel>(
    ActivityCustomDiyPetBinding::inflate,
    CustomDiyPetViewModel::class.java
) {

    private enum class Tool { CAT, EYES, KEYBOARD, TEXT, BACKGROUND, STICKER }
    private enum class LayerKind { CAT, EYES, KEYBOARD, TAG, BACKGROUND, STICKER }

    private class KeyboardEntry(val label: String, var draw: DrawableDraw? = null)

    private val keyboardEntries = mutableListOf<KeyboardEntry>()
    private var keyboardTextDraw: DrawableDraw? = null
    private var keyboardTextJob: Job? = null
    private val imageJobs = mutableSetOf<Job>()
    private var selectedStickerCategory = 0
    private var renderedCatalogue: DiyCatalogue? = null

    private val drawAdapter = DiyDrawAdapter { draw ->
        binding.drawView.selectCurrentDraw(draw)
    }
    private val catStickerAdapter = DiyCatStickerAdapter { path ->
        addImage(path, LayerKind.CAT, replaceExisting = false)
    }
    private val eyeAdapter = DiyCatStickerAdapter { path ->
        addImage(path, LayerKind.EYES, replaceExisting = false)
    }
    private val tagAdapter = DiyCatStickerAdapter { path ->
        addImage(path, LayerKind.TAG)
    }
    private val stickerAdapter = DiyCatStickerAdapter { path ->
        addImage(path, LayerKind.STICKER, replaceExisting = false)
    }
    private val bgViewModel: AddCharacterViewModel by viewModels()
    private val bgImageAdapter by lazy { BackgroundImageAdapter() }
    private val bgColorAdapter by lazy { BackgroundColorAdapter() }
    private var loadedBgQuantity = 0
    private val stickerTitleAdapter = DiyCatTitleAdapter(::selectStickerCategory)
    private var stickerCategories: List<DiyAssetCategory> = emptyList()
    private val keyboardAdapters = List(4) { row ->
        DiyKeyboardAdapter(
            onItemClick = { path, key ->
                val entry = appendKeyboardText(key)
                addImage(path, LayerKind.KEYBOARD, replaceExisting = false, keyboardEntry = entry)
            },
            onDeleteClick = {
                if (row == 3) deleteCurrentKeyboardDraw()
            }
        )
    }
    private val catTitleAdapter = DiyCatTitleAdapter(::selectCatCategory)
    private var currentTool = Tool.CAT
    private var catDraw: DrawableDraw? = null
    private var eyesDraw: DrawableDraw? = null
    private var tagDraw: DrawableDraw? = null
    private var backgroundDraw: DrawableDraw? = null
    private var hasChanges = false
    private var isSaving = false
    private val bgImagePicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                selectBackgroundImage(uri.toString(), 0)
            }
        }
    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.recycleSort.apply {
            layoutManager = LinearLayoutManager(
                this@CustomDiyPetActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = drawAdapter
            itemAnimator = null
        }
        binding.drawView.setOnDrawListener(object : OnDrawListener {
            override fun onAddedDraw(draw: Draw) = refreshDrawList()
            override fun onDeletedDraw(draw: Draw) = handleDeletedDraw(draw)
            override fun onClickedDraw(draw: Draw) = Unit
            override fun onDragFinishedDraw(draw: Draw) { hasChanges = true }
            override fun onTouchedDownDraw(draw: Draw) = Unit
            override fun onZoomFinishedDraw(draw: Draw) { hasChanges = true }
            override fun onFlippedDraw(draw: Draw) { hasChanges = true }
            override fun onDoubleTappedDraw(draw: Draw) = Unit
            override fun onHideOptionIconDraw() = Unit
            override fun onUndoDeleteDraw(draw: List<Draw?>) = Unit
            override fun onUndoUpdateDraw(draw: List<Draw?>) = Unit
            override fun onUndoDeleteAll() = Unit
            override fun onRedoAll() = Unit
            override fun onReplaceDraw(draw: Draw) = Unit
            override fun onEditText(draw: DrawableDraw) = Unit
            override fun onReplace(draw: Draw) = Unit
        })
        binding.rcvSticker.adapter = stickerAdapter
        binding.rcvStickerTitle.adapter = stickerTitleAdapter
        binding.lnlSticker.gone()
        binding.rcvCat.adapter = catStickerAdapter
        binding.rcvCatTitle.adapter = catTitleAdapter
        binding.rcvEge.adapter = eyeAdapter
        binding.recycleTagKeybroad.apply {
            adapter = tagAdapter
            itemAnimator = null
        }
        val keyboardRows = listOf(
            binding.recycleNumber to 10,
            binding.recycleTop to 10,
            binding.recycleMid to 9,
            binding.recycleBottom to 8
        )
        keyboardRows.forEachIndexed { index, (recyclerView, spanCount) ->
            recyclerView.layoutManager = GridLayoutManager(this, spanCount)
            keyboardAdapters[index].setKeyLabels(CustomDiyPetViewModel.KEY_LABELS[index].map(Char::toString))
            recyclerView.adapter = keyboardAdapters[index]
            recyclerView.setHasFixedSize(true)
            recyclerView.itemAnimator = null
            recyclerView.isNestedScrollingEnabled = false
        }
        binding.lnlCat.gone()
        binding.lnlEge.gone()
        binding.lnlBackground.gone()
        initBackgroundPanel()
        // Keep the keyboard measured off-screen so its first reveal does not have to
        // inflate and lay out all four rows on the click frame.
        binding.lnlKeyBroad.invisible()
        selectKeyboardTab(showTags = false)
        binding.recycleSort.invisible()
        viewModel.bindCatalogue(
            viewModelActivity.keybroadBaseUrl,
            viewModelActivity.diyCatCategories,
            viewModelActivity.diyEyesQuantity,
            viewModelActivity.diyKeyboardQuantity,
            viewModelActivity.diyTagQuantity
        )
        renderCatalogue(viewModel.catalogue.value)
        refreshDrawList()

        binding.actionBar.apply {
            tvStart.isSelected = true
            btnActionBarRightText.visibility = View.VISIBLE
            btnActionBarRightText.isClickable = true
            tvRightText.text = getString(R.string.save)
        }

        viewModelActivity.preloadDiyCatCategories()
        updateNavigation(null)
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { handleBack() }
        binding.actionBar.btnActionBarRightText.onClick { saveDesign() }
        binding.btnCat.onClick { toggleCatPanel() }
        binding.btnEge.onClick { toggleEyesPanel() }
        binding.btnKeyBroad.onClick { toggleKeyboardPanel() }
        binding.tagKeyBroad.onClick { selectKeyboardTab(showTags = false) }
        binding.tagTag.onClick { selectKeyboardTab(showTags = true) }
        binding.btnText.onClick { showTool(Tool.TEXT) }
        binding.btnBackground.onClick { toggleBackgroundPanel() }
        binding.btnSticker.onClick { toggleStickerPanel() }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.catalogue.collect(::renderCatalogue) }
                launch { viewModelActivity.diyStickerCategories.collect(::renderStickers) }
                launch { viewModelActivity.diyBackgroundQuantity.collect(::loadBackgroundList) }

            }
        }
    }

    private fun showTool(tool: Tool) {
        currentTool = tool
        updateNavigation(tool)
        refreshDrawList()
        if (tool != Tool.CAT) hideCatPanel()
        if (tool != Tool.EYES) hideEyesPanel()
        if (tool != Tool.KEYBOARD) hideKeyboardPanel()
        if (tool != Tool.STICKER) hideStickerPanel()
        if (tool != Tool.BACKGROUND) hideBackgroundPanel()

    }

    private fun toggleCatPanel() {
        if (currentTool == Tool.CAT && binding.lnlCat.isVisible) {
            hideCatPanel()
            return
        }
        currentTool = Tool.CAT
        updateNavigation(Tool.CAT)
        refreshDrawList()
        showCatPanel()
    }

    private fun toggleEyesPanel() {
        if (currentTool == Tool.EYES && binding.lnlEge.isVisible) {
            hideEyesPanel()
            return
        }
        currentTool = Tool.EYES
        updateNavigation(Tool.EYES)
        refreshDrawList()
        showEyesPanel()
    }

    private fun toggleKeyboardPanel() {
        if (currentTool == Tool.KEYBOARD && binding.lnlKeyBroad.isVisible) {
            hideKeyboardPanel()
            return
        }
        currentTool = Tool.KEYBOARD
        updateNavigation(Tool.KEYBOARD)
        refreshDrawList()
        showKeyboardPanel()
    }

    private fun refreshDrawList() {
        drawAdapter.submitList(binding.drawView.getDraws().toList())
    }

    private fun showCatPanel() {
        hideEyesPanel()
        hideKeyboardPanel()
        hideStickerPanel()
        hideBackgroundPanel()
        binding.lnlCat.visible()
        binding.recycleSort.invisible()
    }

    private fun hideCatPanel() {
        binding.lnlCat.gone()
        restoreSortIfIdle()
    }

    private fun showEyesPanel() {
        hideCatPanel()
        hideKeyboardPanel()
        hideStickerPanel()
        hideBackgroundPanel()
        binding.lnlEge.visible()
        binding.recycleSort.invisible()
    }

    private fun hideEyesPanel() {
        binding.lnlEge.gone()
        restoreSortIfIdle()
    }

    private fun showKeyboardPanel() {
        hideCatPanel()
        hideEyesPanel()
        hideStickerPanel()
        hideBackgroundPanel()
        binding.lnlKeyBroad.visible()
        binding.recycleSort.invisible()
    }

    private fun hideKeyboardPanel() {
        // INVISIBLE keeps the already-created key views warm without intercepting input.
        binding.lnlKeyBroad.invisible()
        restoreSortIfIdle()
    }
    private fun restoreSortIfIdle() {
        if (!binding.lnlCat.isVisible && !binding.lnlEge.isVisible &&
            !binding.lnlSticker.isVisible && !binding.lnlBackground.isVisible &&
            binding.lnlKeyBroad.visibility != View.VISIBLE
        ) {
            binding.recycleSort.visible()
        }
    }
    private fun toggleStickerPanel() {
        if (currentTool == Tool.STICKER && binding.lnlSticker.isVisible) {
            hideStickerPanel()
            return
        }
        currentTool = Tool.STICKER
        updateNavigation(Tool.STICKER)
        refreshDrawList()
        showStickerPanel()
    }

    private fun showStickerPanel() {
        hideCatPanel()
        hideEyesPanel()
        hideKeyboardPanel()
        hideBackgroundPanel()
        binding.lnlSticker.visible()
        binding.recycleSort.invisible()
    }

    private fun hideStickerPanel() {
        binding.lnlSticker.gone()
        if (!binding.lnlCat.isVisible && !binding.lnlEge.isVisible &&
            binding.lnlKeyBroad.visibility != View.VISIBLE
        ) {
            binding.recycleSort.visible()
        }
    }

    private fun renderStickers(categories: List<DiyAssetCategory>) {
        stickerCategories = categories
        selectedStickerCategory = selectedStickerCategory.coerceIn(0, categories.lastIndex.coerceAtLeast(0))
        stickerTitleAdapter.submitList(categories.map { it.category.replace('_', ' ') })
        stickerTitleAdapter.setSelectedPosition(selectedStickerCategory)
        showStickerCategory()
    }

    private fun showStickerCategory() {
        val category = stickerCategories.getOrNull(selectedStickerCategory) ?: return
        stickerAdapter.submitList(
            (1..category.quantity).map {
                "${viewModelActivity.keybroadBaseUrl}/Stickers/${category.category}/$it.png"
            }
        )
    }

    private fun selectStickerCategory(position: Int) {
        selectedStickerCategory = position
        showStickerCategory()
    }
    private fun selectKeyboardTab(showTags: Boolean) {
        if (showTags) {
            binding.keyBroad.gone()
            binding.recycleTagKeybroad.visible()
            val tags = viewModel.catalogue.value.tags
            if (tagAdapter.itemCount != tags.size) tagAdapter.submitList(tags)
            binding.recycleTagKeybroad.post {
                binding.recycleTagKeybroad.requestLayout()
                binding.recycleTagKeybroad.scrollToPosition(0)
            }
        } else {
            binding.recycleTagKeybroad.gone()
            binding.keyBroad.visible()
        }
        binding.tagKeyBroad.apply {
            setTextColor(ContextCompat.getColor(context, if (showTags) R.color.origin2 else R.color.app_color))
            setBackgroundResource(if (showTags) R.drawable.bg_unselected_key else R.drawable.bg_selected_key)
        }
        binding.tagTag.apply {
            setTextColor(ContextCompat.getColor(context, if (showTags) R.color.app_color else R.color.origin2))
            setBackgroundResource(if (showTags) R.drawable.bg_selected_key else R.drawable.bg_unselected_key)
        }
    }

    private fun renderCatalogue(catalogue: DiyCatalogue) {
        val previous = renderedCatalogue
        if (previous == catalogue) return
        if (previous?.cats != catalogue.cats) {
            catTitleAdapter.submitList(catalogue.cats.map { it.title })
        }
        if (previous?.cats != catalogue.cats || previous?.selectedCatCategory != catalogue.selectedCatCategory) {
            catTitleAdapter.setSelectedPosition(catalogue.selectedCatCategory)
            catStickerAdapter.submitList(catalogue.cats.getOrNull(catalogue.selectedCatCategory)?.paths.orEmpty())
        }
        if (previous?.eyes != catalogue.eyes) eyeAdapter.submitList(catalogue.eyes)
        if (previous?.tags != catalogue.tags) tagAdapter.submitList(catalogue.tags)
        if (previous?.keyboardRows != catalogue.keyboardRows) {
            keyboardAdapters.forEachIndexed { index, adapter ->
                adapter.submitKeyboardList(catalogue.keyboardRows.getOrElse(index) { emptyList() }, index == 3)
            }
        }
        renderedCatalogue = catalogue
    }

    private fun selectCatCategory(position: Int) = viewModel.selectCatCategory(position)

    private fun updateNavigation(selected: Tool?) {
        binding.btnCat.setImageResource(
            if (selected == Tool.CAT) R.drawable.ic_cat_select_pet else R.drawable.ic_cat_pet
        )
        binding.btnEge.setImageResource(
            if (selected == Tool.EYES) R.drawable.ic_ege_select_pet else R.drawable.ic_ege_pet
        )
        binding.btnKeyBroad.setImageResource(
            if (selected == Tool.KEYBOARD) {
                R.drawable.ic_keybroad_select_pet
            } else {
                R.drawable.ic_keybroad_pet
            }
        )
        binding.btnText.setImageResource(
            if (selected == Tool.TEXT) R.drawable.ic_text_select_pet else R.drawable.ic_text_pet
        )
        binding.btnBackground.setImageResource(
            if (selected == Tool.BACKGROUND) R.drawable.ic_bg_select_pet else R.drawable.ic_bg_pet
        )
        binding.btnSticker.setImageResource(
            if (selected == Tool.STICKER) {
                R.drawable.ic_sticker_select_pet
            } else {
                R.drawable.ic_sticker_pet
            }
        )
    }
    private fun toggleBackgroundPanel() {
        if (currentTool == Tool.BACKGROUND && binding.lnlBackground.isVisible) {
            hideBackgroundPanel()
            return
        }
        currentTool = Tool.BACKGROUND
        updateNavigation(Tool.BACKGROUND)
        refreshDrawList()
        showBackgroundPanel()
    }

    private fun showBackgroundPanel() {
        hideCatPanel()
        hideEyesPanel()
        hideKeyboardPanel()
        hideStickerPanel()
        binding.lnlBackground.visible()
        binding.recycleSort.invisible()
    }

    private fun hideBackgroundPanel() {
        binding.lnlBackground.gone()
        if (!binding.lnlCat.isVisible && !binding.lnlEge.isVisible &&
            !binding.lnlSticker.isVisible && binding.lnlKeyBroad.visibility != View.VISIBLE
        ) {
            binding.recycleSort.visible()
        }
    }

    private fun initBackgroundPanel() {
        val bg = binding.lnlBackgroundDiy
        bg.btnBackgroundImageTv.isSelected = true
        bg.btnBackgroundColorTv.isSelected = true
        bg.rcvBackgroundImage.apply {
            adapter = bgImageAdapter
            itemAnimator = null
        }
        bg.rcvBackgroundColor.apply {
            adapter = bgColorAdapter
            itemAnimator = null
        }
        bg.btnBackgroundImage.onClick { showBackgroundTab(isImage = true) }
        bg.btnBackgroundColor.onClick { showBackgroundTab(isImage = false) }

        bgImageAdapter.onAddImageClick = {
            runCatching {
                bgImagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
        bgImageAdapter.onNoneImageClick = { position ->
            removeBackground()
            selectBackgroundItem(position, isImage = true)
        }
        bgImageAdapter.onBackgroundImageClick = { path, position ->
            selectBackgroundImage(path, position)
        }
        bgColorAdapter.onChooseColorClick = {
            val dialog = ChooseColorDialog(this)
            dialog.show()
            dialog.onCloseEvent = { dialog.dismiss() }
            dialog.onDoneEvent = { color ->
                dialog.dismiss()
                selectBackgroundColor(color, 0)
            }
        }
        bgColorAdapter.onBackgroundColorClick = { color, position ->
            selectBackgroundColor(color, position)
        }
    }

    private fun showBackgroundTab(isImage: Boolean) {
        val bg = binding.lnlBackgroundDiy
        bg.rcvBackgroundImage.isVisible = isImage
        bg.rcvBackgroundColor.isVisible = !isImage
        bg.btnBackgroundImage.setBackgroundResource(
            if (isImage) R.drawable.img_bg_image_addcharacter else R.drawable.img_bg_color_addcharacter
        )
        bg.btnBackgroundColor.setBackgroundResource(
            if (isImage) R.drawable.img_bg_color_addcharacter else R.drawable.img_bg_image_addcharacter
        )
        bg.btnBackgroundImageTv.setTextColor(
            ContextCompat.getColor(this, if (isImage) R.color.app_color else R.color.origin2)
        )
        bg.btnBackgroundColorTv.setTextColor(
            ContextCompat.getColor(this, if (isImage) R.color.origin2 else R.color.app_color)
        )
    }

    private fun loadBackgroundList(quantity: Int) {
        if (quantity <= 0 || quantity == loadedBgQuantity) return
        loadedBgQuantity = quantity
        bgViewModel.hasLoadedData = false
        bgViewModel.loadDataFromQuantity(
            bgQuantity = quantity,
            stickerQuantity = 1,
            bgBaseUrl = viewModelActivity.keybroadBaseUrl,
            speeches = listOf("")
        )
        bgImageAdapter.submitList(bgViewModel.backgroundImageList)
        bgColorAdapter.submitList(bgViewModel.backgroundColorList, true)
    }

    private fun selectBackgroundImage(path: String, position: Int) {
        bgViewModel.savedBackgroundColor = null
        selectBackgroundItem(position, isImage = true)
        val job = lifecycleScope.launch {
            try {
                val bitmap = viewModel.loadCanvasImage(path, trimPadding = false)
                if (isFinishing || isDestroyed) return@launch
                binding.drawView.background = bitmap.toDrawable(resources)
                hasChanges = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast(R.string.image_load_failed)
            }
        }
        imageJobs += job
        job.invokeOnCompletion { imageJobs.remove(job) }
    }

    private fun selectBackgroundColor(color: Int, position: Int) {
        bgViewModel.savedBackgroundColor = color
        binding.drawView.setBackgroundColor(color)
        hasChanges = true
        selectBackgroundItem(position, isImage = false)
    }

    private fun removeBackground() {
        binding.drawView.setBackgroundColor(Color.WHITE)
        hasChanges = true
    }

    private fun selectBackgroundItem(position: Int, isImage: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (isImage) bgViewModel.updateBackgroundImageSelected(position)
            else bgViewModel.updateBackgroundColorSelected(position)
            withContext(Dispatchers.Main) {
                if (isImage) {
                    bgColorAdapter.clearSelection()
                    bgImageAdapter.selectItem(position)
                } else {
                    bgImageAdapter.clearSelection()
                    bgColorAdapter.selectItem(position)
                }
            }
        }
    }
    private fun addImage(
        path: String,
        kind: LayerKind,
        replaceExisting: Boolean = true,
        keyboardEntry: KeyboardEntry? = null
    ) {
        val job = lifecycleScope.launch {
            try {
                val bitmap = viewModel.loadCanvasImage(
                    path,
                    trimPadding = kind == LayerKind.KEYBOARD || kind == LayerKind.TAG
                )
                if (isFinishing || isDestroyed) return@launch
                val drawable = bitmap.toDrawable(resources)
                when (kind) {
                    LayerKind.CAT -> {
                        if (replaceExisting) {
                            catDraw = replaceOrAdd(catDraw, drawable, path, isLarge = true)
                        } else {
                            replaceOrAdd(null, drawable, path, isLarge = true)
                        }
                    }
                    LayerKind.EYES -> {
                        if (replaceExisting) {
                            eyesDraw = replaceOrAdd(eyesDraw, drawable, path)
                        } else {
                            replaceOrAdd(null, drawable, path)
                        }
                    }
                    LayerKind.KEYBOARD -> {
                        if (keyboardEntry == null || !keyboardEntries.contains(keyboardEntry)) return@launch
                        // `path` is the exact API URL passed to imgKey by the adapter.
                        keyboardEntry.draw = replaceOrAdd(null, drawable, path)
                        bringKeyboardTextToFront()
                    }
                    LayerKind.TAG -> tagDraw = replaceOrAdd(tagDraw, drawable, path)
                    LayerKind.BACKGROUND -> {
                        backgroundDraw = replaceOrAdd(backgroundDraw, drawable, path, isLarge = true)
                        backgroundDraw?.let { draw ->
                            val position = binding.drawView.getDraws().indexOf(draw)
                            if (position > 0) binding.drawView.moveLayer(position, 0)
                        }
                    }
                    LayerKind.STICKER -> replaceOrAdd(null, drawable, path)
                }
                hasChanges = true
                refreshDrawList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (keyboardEntry != null && keyboardEntries.remove(keyboardEntry)) {
                    updateKeyboardTextDraw()
                    refreshDrawList()
                }
                showToast(R.string.image_load_failed)
            }
        }
        imageJobs += job
        job.invokeOnCompletion { imageJobs.remove(job) }
    }

    private fun replaceOrAdd(
        existing: DrawableDraw?,
        drawable: Drawable,
        path: String,
        isLarge: Boolean = false
    ): DrawableDraw {
        if (existing != null && binding.drawView.getDraws().contains(existing)) {
            existing.setDrawable(drawable)
            existing.setOriginalDrawable(drawable)
            binding.drawView.selectCurrentDraw(existing)
            binding.drawView.invalidate()
            return existing
        }
        return DrawableDraw(drawable, path).also { draw ->
            draw.isCharacter = isLarge
            binding.drawView.addDraw(draw)
        }
    }

    private fun appendKeyboardText(value: String): KeyboardEntry {
        val entry = KeyboardEntry(value)
        keyboardEntries += entry
        updateKeyboardTextDraw(selectText = true)
        hasChanges = true
        return entry
    }

    private fun bringKeyboardTextToFront() {
        val textDraw = keyboardTextDraw ?: return
        val draws = binding.drawView.getDraws()
        val textIndex = draws.indexOf(textDraw)
        if (textIndex >= 0 && textIndex != draws.lastIndex) {
            binding.drawView.moveLayer(textIndex, draws.lastIndex)
        }
    }

    private fun deleteCurrentKeyboardDraw() {
        // Both the keyboard button and the canvas delete icon notify onDeletedDraw.
        binding.drawView.removeDrawCurrent()
    }

    private fun handleDeletedDraw(draw: Draw) {
        if (draw === keyboardTextDraw) {
            keyboardTextJob?.cancel()
            val linkedImages = keyboardEntries.mapNotNull { it.draw }
            // Clear links before cascading deletes, including entries still loading.
            keyboardEntries.clear()
            keyboardTextDraw = null
            linkedImages.forEach { binding.drawView.remove(it) }
        } else if (keyboardEntries.removeAll { it.draw === draw }) {
            updateKeyboardTextDraw()
        }
        hasChanges = true
        refreshDrawList()
    }

    private fun updateKeyboardTextDraw(selectText: Boolean = false) {
        keyboardTextJob?.cancel()
        if (keyboardEntries.isEmpty()) {
            val textDraw = keyboardTextDraw
            keyboardTextDraw = null
            if (textDraw != null) binding.drawView.remove(textDraw)
            return
        }
        val text = keyboardEntries.joinToString("") { it.label }
        // Each edit cancels the previous render; a slow render can never overwrite
        // newer typing or re-create text that the user already deleted.
        keyboardTextJob = lifecycleScope.launch {
            val bitmap = viewModel.renderText(text, Color.BLACK, textSize = 72f)
            val drawable = bitmap.toDrawable(resources)
            val current = keyboardTextDraw
            if (current != null && binding.drawView.getDraws().contains(current)) {
                current.setDrawable(drawable)
                current.setOriginalDrawable(drawable)
                current.textContent = text
                if (selectText) binding.drawView.selectCurrentDraw(current)
            } else {
                val draw = DrawableDraw(drawable, "keyboard-text-${System.nanoTime()}").apply {
                    isText = true
                    textContent = text
                }
                keyboardTextDraw = draw
                binding.drawView.addDraw(draw, DrawKey.TOP)
                moveKeyboardTextNearTop(draw)
            }
            bringKeyboardTextToFront()
            binding.drawView.invalidate()
            refreshDrawList()
        }
    }

    private fun moveKeyboardTextNearTop(draw: DrawableDraw) {
        binding.drawView.post {
            if (draw !== keyboardTextDraw || !binding.drawView.getDraws().contains(draw)) {
                return@post
            }
            val targetTop = resources.getDimension(R.dimen.dimension_16)
            draw.getMatrix().postTranslate(0f, targetTop - draw.mappedBound.top)
            binding.drawView.invalidate()
        }
    }

    private fun showAddTextDialog(textColor: Int) {
        val input = EditText(this).apply {
            hint = getString(R.string.enter_text)
            maxLines = 2
            setTextColor(textColor)
            setHintTextColor(ContextCompat.getColor(context, R.color.color_draw_unselect))
            setPadding(32, 16, 32, 16)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.text)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val content = input.text?.toString()?.trim().orEmpty()
                if (content.isEmpty()) {
                    showToast(R.string.null_edt)
                } else {
                    lifecycleScope.launch {
                        addBitmapLayer(viewModel.renderText(content, textColor), "text")
                    }
                    dialog.dismiss()
                }
            }
            input.requestFocus()
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        }
        dialog.show()
    }

    private fun addBitmapLayer(bitmap: Bitmap, name: String) {
        val draw = DrawableDraw(
            bitmap.toDrawable(resources),
            "$name-${System.nanoTime()}"
        ).apply {
            isText = true
        }
        binding.drawView.addDraw(draw)
        hasChanges = true
        binding.drawView.post { refreshDrawList() }
    }

    private fun saveDesign() {
        if (isSaving) return
        if (binding.drawView.getStickerCount() == 0 && imageJobs.isEmpty() && keyboardEntries.isEmpty()) {
            showToast(R.string.nothing_to_save)
            return
        }

        isSaving = true
        showLoadingSafe()
        lifecycleScope.launch {
            val path = try {
                // Export the final image/text, even if Save follows the last tap
                // before its background decode or text render has finished.
                imageJobs.toList().joinAll()
                keyboardTextJob?.join()
                if (binding.drawView.getStickerCount() == 0) {
                    null
                } else {
                    binding.drawView.hideSelect()
                    viewModel.saveDesign(binding.drawView.save())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                hideLoadingSafe()
                isSaving = false
            }
            if (path == null) {
                showToast(R.string.save_failed)
                return@launch
            }

            hasChanges = false
            startActivity(
                Intent(this@CustomDiyPetActivity, ViewActivity::class.java).apply {
                    putExtra("imagePath", path)
                    putExtra("idEdit", "")
                    putExtra("imageType", 0)
                    putExtra(DesignSource.ARG_SOURCE, DesignSource.DIY)
                }
            )
        }
    }

    private fun handleBack() {
        if (!hasChanges) {
            finish()
            return
        }
        showConfirmDialog(
            message = getString(R.string.haven_t_saved_it_yet_do_you_want_to_exit),
            title = getString(R.string.exit),
            onYes = { finish() }
        )
    }

    override fun onScreenBackPressed(): Boolean {
        handleBack()
        return true
    }

}
