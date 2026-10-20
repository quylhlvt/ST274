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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.saveToFile
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.databinding.ActivityCustomDiyPetBinding
import com.oc.catemoji.catoc.ui.main.add_character.AddCharacterViewModel
import com.oc.catemoji.catoc.ui.main.add_character.DesignSource
import com.oc.catemoji.catoc.ui.main.view.ViewActivity
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CustomDiyPetActivity : BaseActivity<ActivityCustomDiyPetBinding, AddCharacterViewModel>(
    ActivityCustomDiyPetBinding::inflate,
    AddCharacterViewModel::class.java
) {

    private enum class Tool { CAT, EYES, KEYBOARD, TEXT, STICKER }
    private enum class LayerKind { CAT, EYES, STICKER }

    private val assetAdapter = DiyAssetAdapter(::onAssetClicked)
    private val selectedPositions = mutableMapOf<Tool, Int>()
    private var currentTool = Tool.CAT
    private var catDraw: DrawableDraw? = null
    private var eyesDraw: DrawableDraw? = null
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
            adapter = assetAdapter
            itemAnimator = null
        }

        binding.actionBar.apply {
            btnActionBarLeft.visibility = View.VISIBLE
            tvStart.text = getString(R.string.diy_pet_language)
            tvStart.isSelected = true
            btnActionBarRightText.visibility = View.VISIBLE
            btnActionBarRightText.isClickable = true
            tvRightText.text = getString(R.string.save)
        }

        viewModelActivity.preloadBackgroundsAndStickers()
        viewModelActivity.loadStudioCatalogue()
        showTool(Tool.CAT)
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { handleBack() }
        binding.actionBar.btnActionBarRightText.onClick { saveDesign() }
        binding.btnCat.onClick { showTool(Tool.CAT) }
        binding.btnEge.onClick { showTool(Tool.EYES) }
        binding.btnKeyBroad.onClick { showTool(Tool.KEYBOARD) }
        binding.btnText.onClick { showTool(Tool.TEXT) }
        binding.btnSticker.onClick { showTool(Tool.STICKER) }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModelActivity.templates.collect { refreshCurrentTool() } }
                launch { viewModelActivity.customizedCharacters.collect { refreshCurrentTool() } }
                launch { viewModelActivity.studioCatalogue.collect { refreshCurrentTool() } }
                launch { viewModelActivity.stickers.collect { refreshCurrentTool() } }
                launch { viewModelActivity.speechs.collect { refreshCurrentTool() } }
                launch { viewModelActivity.bgStickerReady.collect { refreshCurrentTool() } }
            }
        }
    }

    private fun showTool(tool: Tool) {
        currentTool = tool
        updateNavigation(tool)
        refreshCurrentTool()
    }

    private fun updateNavigation(selected: Tool) {
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
        binding.btnSticker.setImageResource(
            if (selected == Tool.STICKER) {
                R.drawable.ic_sticker_select_pet
            } else {
                R.drawable.ic_sticker_pet
            }
        )
    }

    private fun refreshCurrentTool() {
        val items = when (currentTool) {
            Tool.CAT -> catItems()
            Tool.EYES -> eyeItems()
            Tool.KEYBOARD -> KEYBOARD_KEYS.map { DiyAssetItem.Key(it.toString()) }
            Tool.TEXT -> DataLocal.getTextColorDefault(this).map {
                DiyAssetItem.Color(it.color)
            }
            Tool.STICKER -> stickerItems()
        }
        val selected = selectedPositions[currentTool]
            ?.takeIf { it in items.indices }
            ?: RecyclerView.NO_POSITION
        assetAdapter.submitList(items, selected)
    }

    private fun catItems(): List<DiyAssetItem> {
        val customized = viewModelActivity.customizedCharacters.value.mapNotNull { model ->
            model.imageSave.ifBlank { model.avatar }.takeIf(String::isNotBlank)
        }
        val templates = viewModelActivity.templates.value.mapNotNull { model ->
            model.avatar.takeIf(String::isNotBlank)
        }
        return (customized + templates)
            .distinct()
            .take(MAX_ITEMS)
            .map(DiyAssetItem::Image)
    }

    private fun eyeItems(): List<DiyAssetItem> {
        val parts = viewModelActivity.templates.value.flatMap { it.listPath } +
            viewModelActivity.studioCatalogue.value
        return parts
            .filter { it.nav.contains("eye", ignoreCase = true) }
            .flatMap(::pathsFromPart)
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_ITEMS)
            .map(DiyAssetItem::Image)
    }

    private fun pathsFromPart(part: BodyPartModel): List<String> {
        val renderPaths = buildList {
            addAll(part.listSinglePath)
            part.listPath.forEach { addAll(it.listPath) }
        }
        return renderPaths.ifEmpty { part.listThumbPath }
    }

    private fun stickerItems(): List<DiyAssetItem> {
        val loaded = viewModelActivity.stickers.value
        val remote = if (loaded.isNotEmpty()) loaded else {
            (1..viewModelActivity.stickerQuantity).map {
                "${viewModelActivity.bgBaseUrl}/Sticker/$it.png"
            }
        }
        val paths = if (remote.isNotEmpty()) remote else viewModelActivity.speechs.value
        return paths
            .distinct()
            .take(MAX_ITEMS)
            .map(DiyAssetItem::Image)
    }

    private fun onAssetClicked(item: DiyAssetItem, position: Int) {
        selectedPositions[currentTool] = position
        assetAdapter.select(position)
        when (currentTool) {
            Tool.CAT -> (item as? DiyAssetItem.Image)?.let {
                addImage(it.path, LayerKind.CAT)
            }
            Tool.EYES -> (item as? DiyAssetItem.Image)?.let {
                addImage(it.path, LayerKind.EYES)
            }
            Tool.KEYBOARD -> (item as? DiyAssetItem.Key)?.let {
                addKeyboardKey(it.value)
            }
            Tool.TEXT -> (item as? DiyAssetItem.Color)?.let {
                showAddTextDialog(it.value)
            }
            Tool.STICKER -> (item as? DiyAssetItem.Image)?.let {
                addImage(it.path, LayerKind.STICKER)
            }
        }
    }

    private fun addImage(path: String, kind: LayerKind) {
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
                            catDraw = replaceOrAdd(catDraw, drawable, path, isLarge = true)
                        }
                        LayerKind.EYES -> {
                            eyesDraw = replaceOrAdd(eyesDraw, drawable, path)
                        }
                        LayerKind.STICKER -> replaceOrAdd(null, drawable, path)
                    }
                    hasChanges = true
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit

                override fun onLoadFailed(errorDrawable: Drawable?) {
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

    private fun addKeyboardKey(value: String) {
        addBitmapLayer(
            createTextBitmap(value, Color.WHITE, tile = true),
            "keyboard_$value"
        )
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
    }

    private fun createTextBitmap(text: String, color: Int, tile: Boolean): Bitmap {
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
        paint.textSize = 150f
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
        private const val KEYBOARD_KEYS = "1234567890QWERTYUIOPASDFGHJKLZXCVBNM"
    }
}
