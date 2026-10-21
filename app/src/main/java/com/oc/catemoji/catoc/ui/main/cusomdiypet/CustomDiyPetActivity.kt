package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.custom.Draw
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.core.custom.listener.listenerdraw.OnDrawListener
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.saveToFile
import com.oc.catemoji.catoc.databinding.ActivityCustomDiyPetBinding
import com.oc.catemoji.catoc.ui.main.add_character.AddCharacterViewModel
import com.oc.catemoji.catoc.ui.main.add_character.DesignSource
import com.oc.catemoji.catoc.ui.main.view.ViewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CustomDiyPetActivity : BaseActivity<ActivityCustomDiyPetBinding, AddCharacterViewModel>(
    ActivityCustomDiyPetBinding::inflate,
    AddCharacterViewModel::class.java
) {

    private enum class Tool { CAT, EYES, KEYBOARD, TEXT, BACKGROUND, STICKER }
    private enum class LayerKind { CAT, EYES, KEYBOARD, BACKGROUND, STICKER }

    private var keyboardPanelLoaded = false
    private val preloadedKeyboardImages = HashSet<String>()
    private class KeyboardEntry(val label: String, var draw: DrawableDraw? = null)

    private val keyboardEntries = mutableListOf<KeyboardEntry>()
    private var keyboardTextDraw: DrawableDraw? = null

    private val drawAdapter = DiyDrawAdapter { draw ->
        binding.drawView.selectCurrentDraw(draw)
    }
    private val catStickerAdapter = DiyCatStickerAdapter { path ->
        addImage(path, LayerKind.CAT, replaceExisting = false)
    }
    private val eyeAdapter = DiyCatStickerAdapter { path ->
        addImage(path, LayerKind.EYES, replaceExisting = false)
    }
    private val keyboardAdapters = listOf(
        DiyKeyboardAdapter(onItemClick = { path, key ->
            val entry = appendKeyboardText(key)
            addImage(path, LayerKind.KEYBOARD, replaceExisting = false, keyboardEntry = entry)
        }),
        DiyKeyboardAdapter(onItemClick = { path, key ->
            val entry = appendKeyboardText(key)
            addImage(path, LayerKind.KEYBOARD, replaceExisting = false, keyboardEntry = entry)
        }),
        DiyKeyboardAdapter(onItemClick = { path, key ->
            val entry = appendKeyboardText(key)
            addImage(path, LayerKind.KEYBOARD, replaceExisting = false, keyboardEntry = entry)
        }),
        DiyKeyboardAdapter(
            onItemClick = { path, key ->
                val entry = appendKeyboardText(key)
                addImage(path, LayerKind.KEYBOARD, replaceExisting = false, keyboardEntry = entry)
            },
            onDeleteClick = {
                deleteCurrentKeyboardDraw()
            }
        )
    )
    private val catTitleAdapter = DiyCatTitleAdapter(::selectCatCategory)
    private var currentTool = Tool.CAT
    private var catCategories: List<CatCategory> = emptyList()
    private var selectedCatCategory = 0
    private var catDraw: DrawableDraw? = null
    private var eyesDraw: DrawableDraw? = null
    private var backgroundDraw: DrawableDraw? = null
    private var hasChanges = false
    private var isSaving = false

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
            override fun onDragFinishedDraw(draw: Draw) = Unit
            override fun onTouchedDownDraw(draw: Draw) = Unit
            override fun onZoomFinishedDraw(draw: Draw) = Unit
            override fun onFlippedDraw(draw: Draw) = Unit
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
        binding.rcvCat.adapter = catStickerAdapter
        binding.rcvCatTitle.adapter = catTitleAdapter
        binding.rcvEge.adapter = eyeAdapter
        val keyboardRows = listOf(
            binding.recycleNumber to 10,
            binding.recycleTop to 10,
            binding.recycleMid to 9,
            binding.recycleBottom to 8
        )
        val keyboardLabels = listOf(
            "1234567890".map(Char::toString),
            "QWERTYUIOP".map(Char::toString),
            "ASDFGHJKL".map(Char::toString),
            "ZXCVBNM".map(Char::toString)
        )
        keyboardRows.forEachIndexed { index, (recyclerView, spanCount) ->
            recyclerView.layoutManager = GridLayoutManager(this, spanCount)
            keyboardAdapters[index].setKeyLabels(keyboardLabels[index])
            recyclerView.adapter = keyboardAdapters[index]
            recyclerView.setHasFixedSize(true)
            recyclerView.itemAnimator = null
            recyclerView.isNestedScrollingEnabled = false
        }
        binding.lnlCat.isVisible = false
        binding.lnlEge.isVisible = false
        binding.lnlKeyBroad.isVisible = false
        binding.recycleSort.isVisible = true
        refreshDrawList()

        binding.actionBar.apply {
            tvStart.isSelected = true
            btnActionBarRightText.visibility = View.VISIBLE
            btnActionBarRightText.isClickable = true
            tvRightText.text = getString(R.string.save)
        }

        viewModelActivity.preloadBackgroundsAndStickers()
        viewModelActivity.preloadDiyCatCategories()
        viewModelActivity.loadStudioCatalogue()
        refreshDrawList()
        updateNavigation(null)
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { handleBack() }
        binding.actionBar.btnActionBarRightText.onClick { saveDesign() }
        binding.btnCat.onClick { toggleCatPanel() }
        binding.btnEge.onClick { toggleEyesPanel() }
        binding.btnKeyBroad.onClick { toggleKeyboardPanel() }
        binding.btnText.onClick { showTool(Tool.TEXT) }
        binding.btnBackground.onClick { showTool(Tool.BACKGROUND) }
        binding.btnSticker.onClick { showTool(Tool.STICKER) }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModelActivity.templates.collect { refreshCatPanel() } }
                launch { viewModelActivity.customizedCharacters.collect { refreshCatPanel() } }
                launch { viewModelActivity.studioCatalogue.collect { refreshCatPanel() } }
                launch { viewModelActivity.diyCatCategories.collect { refreshCatPanel() } }
                launch { viewModelActivity.diyEyesQuantity.collect { refreshEyesPanel() } }
                launch { viewModelActivity.diyKeyboardQuantity.collect { refreshKeyboardPanel() } }
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
        refreshCatPanel()
        hideEyesPanel()
        hideKeyboardPanel()
        binding.lnlCat.isVisible = true
        binding.recycleSort.isVisible = false
    }

    private fun hideCatPanel() {
        binding.lnlCat.isVisible = false
        binding.recycleSort.isVisible = true
    }

    private fun showEyesPanel() {
        refreshEyesPanel()
        hideCatPanel()
        hideKeyboardPanel()
        binding.lnlEge.isVisible = true
        binding.recycleSort.isVisible = false
    }

    private fun hideEyesPanel() {
        binding.lnlEge.isVisible = false
        if (!binding.lnlCat.isVisible) binding.recycleSort.isVisible = true
    }

    private fun showKeyboardPanel() {
        if (!keyboardPanelLoaded) {
            refreshKeyboardPanel()
            keyboardPanelLoaded = true
        }
        hideCatPanel()
        hideEyesPanel()
        binding.lnlKeyBroad.isVisible = true
        binding.recycleSort.isVisible = false
    }

    private fun hideKeyboardPanel() {
        binding.lnlKeyBroad.isVisible = false
        if (!binding.lnlCat.isVisible && !binding.lnlEge.isVisible) {
            binding.recycleSort.isVisible = true
        }
    }

    private fun refreshEyesPanel() {
        val quantity = viewModelActivity.diyEyesQuantity.value
            .takeIf { it > 0 }
            ?: DEFAULT_EYES_QUANTITY
        val paths = (1..quantity).map { index ->
            "${viewModelActivity.keybroadBaseUrl}/Eyes/$index.png"
        }
        eyeAdapter.submitList(paths)
    }

    private fun refreshKeyboardPanel() {
        val quantity = viewModelActivity.diyKeyboardQuantity.value
            .takeIf { it > 0 }
            ?: DEFAULT_KEYBOARD_QUANTITY
        val paths = (1..quantity).map { index ->
                "${viewModelActivity.keybroadBaseUrl}/Keybroad/Keybroad/$index.png"
        }
        preloadKeyboardImages(paths)
        val rows = listOf(10, 10, 9, 7)
        var start = 0
        keyboardAdapters.forEachIndexed { index, adapter ->
            val end = minOf(start + rows[index], paths.size)
            val rowItems = paths.subList(start, end).toMutableList()
            if (index == keyboardAdapters.lastIndex) {
                rowItems += DELETE_SENTINEL
            }
            adapter.submitKeyboardList(
                rowItems,
                showDeleteAtEnd = index == keyboardAdapters.lastIndex
            )
            start = end
        }
    }

    private fun preloadKeyboardImages(paths: List<String>) {
        paths.asSequence()
            .filter { preloadedKeyboardImages.add(it) }
            .forEach { path ->
                Glide.with(this)
                    .load(path)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(96, 96)
                    .preload()
            }
    }

    private fun refreshCatPanel() {
        catCategories = catCategories()
        if (catCategories.isEmpty()) {
            catTitleAdapter.submitList(emptyList())
            catStickerAdapter.submitList(emptyList())
            return
        }

        selectedCatCategory = selectedCatCategory.coerceIn(0, catCategories.lastIndex)
        catTitleAdapter.submitList(catCategories.map { it.title })
        catTitleAdapter.setSelectedPosition(selectedCatCategory)
        catStickerAdapter.submitList(catCategories[selectedCatCategory].paths)
    }

    private fun selectCatCategory(position: Int) {
        if (position !in catCategories.indices) return
        selectedCatCategory = position
        catStickerAdapter.submitList(catCategories[position].paths)
    }

    private fun catCategories(): List<CatCategory> {
        return viewModelActivity.diyCatCategories.value.map { category ->
            val paths = (1..category.quantity).map { index ->
                "${viewModelActivity.keybroadBaseUrl}/Cat/${category.category}/$index.png"
            }
            CatCategory(category.category.replace('_', ' '), paths)
        }
    }

    private data class CatCategory(val title: String, val paths: List<String>)

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

    private fun addImage(
        path: String,
        kind: LayerKind,
        replaceExisting: Boolean = true,
        keyboardEntry: KeyboardEntry? = null
    ) {
        Glide.with(this)
            .asBitmap()
            .load(path)
            .override(768, 768)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .disallowHardwareConfig()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    if (isFinishing || isDestroyed) return
                    val drawable = resource.copy(Bitmap.Config.ARGB_8888, true)
                        .toDrawable(resources)
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
                            if (keyboardEntry == null || !keyboardEntries.contains(keyboardEntry)) {
                                return
                            }
                            val draw = replaceOrAdd(null, drawable, path)
                            keyboardEntry.draw = draw
                            bringKeyboardTextToFront()
                        }
                        LayerKind.BACKGROUND -> {
                            backgroundDraw = replaceOrAdd(
                                backgroundDraw,
                                drawable,
                                path,
                                isLarge = true
                            )
                            backgroundDraw?.let { draw ->
                                val position = binding.drawView.getDraws().indexOf(draw)
                                if (position > 0) {
                                    binding.drawView.exchangeLayers(position, 0)
                                }
                            }
                        }
                        LayerKind.STICKER -> replaceOrAdd(null, drawable, path)
                    }
                    hasChanges = true
                    binding.drawView.post { refreshDrawList() }
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (keyboardEntry != null && keyboardEntries.remove(keyboardEntry)) {
                        updateKeyboardTextDraw()
                        refreshDrawList()
                    }
                    showToast(R.string.image_load_failed)
                }
            })
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
        val bitmap = createTextBitmap(
            keyboardEntries.joinToString("") { it.label },
            Color.BLACK,
            tile = false,
            textSize = 72f
        )
        val current = keyboardTextDraw
        if (current != null && binding.drawView.getDraws().contains(current)) {
            current.setDrawable(bitmap.toDrawable(resources))
            current.setOriginalDrawable(bitmap.toDrawable(resources))
            current.textContent = keyboardEntries.joinToString("") { it.label }
            binding.drawView.selectCurrentDraw(current)
        } else {
            keyboardTextDraw = DrawableDraw(
                bitmap.toDrawable(resources),
                "keyboard-text-${System.nanoTime()}"
            ).apply {
                isText = true
                textContent = keyboardEntries.joinToString("") { it.label }
            }
            binding.drawView.addDraw(keyboardTextDraw!!)
        }
        bringKeyboardTextToFront()
        hasChanges = true
        binding.drawView.post { refreshDrawList() }
        return entry
    }

    private fun bringKeyboardTextToFront() {
        val textDraw = keyboardTextDraw ?: return
        val draws = binding.drawView.getDraws()
        val textIndex = draws.indexOf(textDraw)
        if (textIndex >= 0 && textIndex != draws.lastIndex) {
            binding.drawView.exchangeLayers(textIndex, draws.lastIndex)
        }
    }

    private fun deleteCurrentKeyboardDraw() {
        // Both the keyboard button and the canvas delete icon notify onDeletedDraw.
        binding.drawView.removeDrawCurrent()
    }

    private fun handleDeletedDraw(draw: Draw) {
        if (draw === keyboardTextDraw) {
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

    private fun updateKeyboardTextDraw() {
        val textDraw = keyboardTextDraw ?: return
        if (keyboardEntries.isEmpty()) {
            binding.drawView.remove(textDraw)
            keyboardTextDraw = null
            return
        }
        val text = keyboardEntries.joinToString("") { it.label }
        val bitmap = createTextBitmap(text, Color.BLACK, tile = false, textSize = 72f)
        textDraw.setDrawable(bitmap.toDrawable(resources))
        textDraw.setOriginalDrawable(bitmap.toDrawable(resources))
        textDraw.textContent = text
        bringKeyboardTextToFront()
        binding.drawView.invalidate()
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
                    addBitmapLayer(
                        createTextBitmap(content, textColor, tile = false),
                        "text"
                    )
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

    private fun createTextBitmap(
        text: String,
        color: Int,
        tile: Boolean,
        textSize: Float = 150f
    ): Bitmap {
        val width = if (tile) 240 else 900
        val height = if (tile) 240 else 260
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        if (tile) {
            paint.color = getColor(R.color.app_color2)
            canvas.drawRoundRect(8f, 8f, width - 8f, height - 8f, 48f, 48f, paint)
        }

        paint.color = color
        paint.textSize = textSize
        val maxTextWidth = width * 0.9f
        while (paint.measureText(text) > maxTextWidth && paint.textSize > 38f) {
            paint.textSize -= 4f
        }
        val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, width / 2f, baseline, paint)
        return bitmap
    }

    private fun saveDesign() {
        if (isSaving) return
        if (binding.drawView.getStickerCount() == 0) {
            showToast(R.string.nothing_to_save)
            return
        }

        binding.drawView.hideSelect()
        isSaving = true
        showLoadingSafe()
        binding.drawView.post {
            val bitmap = runCatching { binding.drawView.save() }.getOrNull()
            if (bitmap == null) {
                finishSavingWithError()
                return@post
            }

            lifecycleScope.launch {
                val path = withContext(Dispatchers.IO) {
                    val savedPath = bitmap.saveToFile(
                        this@CustomDiyPetActivity,
                        "diy_pet"
                    )
                    if (savedPath != null) {
                        viewModelActivity.appDataManager.addMyDesignPath(
                            savedPath,
                            DesignSource.DIY
                        )
                    }
                    bitmap.recycle()
                    savedPath
                }
                hideLoadingSafe()
                isSaving = false
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
    }

    private fun finishSavingWithError() {
        hideLoadingSafe()
        isSaving = false
        showToast(R.string.save_failed)
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

    companion object {
        private const val MAX_ITEMS = 120
        private const val DEFAULT_EYES_QUANTITY = 33
        private const val DEFAULT_KEYBOARD_QUANTITY = 36
        private const val DELETE_SENTINEL = "__delete__"
        private const val KEYBOARD_KEYS = "1234567890QWERTYUIOPASDFGHJKLZXCVBNM"
    }
}
