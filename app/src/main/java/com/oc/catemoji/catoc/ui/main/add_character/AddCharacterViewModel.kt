package com.oc.catemoji.catoc.ui.main.add_character

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.ViewModel
import com.oc.catemoji.catoc.core.custom.Draw
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AddCharacterViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ========== Init guard ==========
    var isInitialized = false
    var isRestoringDraws = false

    // ========== Adapter Lists ==========
    var backgroundImageList: ArrayList<SelectedAddModel> = arrayListOf()
    var backgroundColorList: ArrayList<SelectedAddModel> = arrayListOf()
    var stickerList: ArrayList<SelectedAddModel> = arrayListOf()
    var speechList: ArrayList<SelectedAddModel> = arrayListOf()
    var textFontList: ArrayList<SelectedAddModel> = arrayListOf()
    var textColorList: ArrayList<SelectedAddModel> = arrayListOf()

    // ========== Navigation ==========
    // -1 = chưa set, Fragment bỏ qua
    private val _typeNavigation = MutableStateFlow(-1)
    val typeNavigation: StateFlow<Int> = _typeNavigation.asStateFlow()

    private val _typeBackground = MutableStateFlow(-1)
    val typeBackground: StateFlow<Int> = _typeBackground.asStateFlow()

    // ========== Background ==========
    private val _backgroundImagePath = MutableStateFlow<String?>(null)
    val backgroundImagePath: StateFlow<String?> = _backgroundImagePath.asStateFlow()

    var savedBackgroundColor: Int? = null

    // ========== Tab state ==========
    // Chỉ dùng để biết tab nào đang active — KHÔNG dùng để control layout
    var isTextTabActive: Boolean = false
    var isSpeechDialogOpen: Boolean = false

    // ========== Draw state ==========
    var currentDraw: Draw? = null
    var drawViewList: ArrayList<DrawableDraw> = arrayListOf()

    // ========== Misc ==========
    var pathDefault = ""

    // ========== Navigation setters ==========

    fun setTypeNavigation(type: Int) {
        if (_typeNavigation.value == type) _typeNavigation.value = -1
        _typeNavigation.value = type
    }

    fun setTypeBackground(type: Int) {
        if (_typeBackground.value == type) _typeBackground.value = -1
        _typeBackground.value = type
    }

    fun setBackgroundImage(path: String?) {
        _backgroundImagePath.value = path
    }

    // ========== Data loading ==========

    fun loadDataFromMainViewModel(
        backgrounds: List<String>,
        stickers: List<String>,
        speeches: List<String>
    ) {
        backgroundImageList.clear()
        backgroundImageList.addAll(backgrounds.map { SelectedAddModel(path = it) })

        backgroundColorList.clear()
        backgroundColorList.addAll(DataLocal.getBackgroundColorDefault(context))

        stickerList.clear()
        stickerList.addAll(stickers.map { SelectedAddModel(path = it) })

        speechList.clear()
        speechList.addAll(speeches.map { SelectedAddModel(path = it) })

        textFontList.clear()
        textFontList.addAll(DataLocal.getTextFontDefault())
        textFontList.firstOrNull()?.isSelected = true

        textColorList.clear()
        textColorList.addAll(DataLocal.getTextColorDefault(context))
        textColorList.getOrNull(1)?.isSelected = true
    }

    // ========== Selection helpers ==========

    suspend fun updateBackgroundImageSelected(position: Int) {
        withContext(Dispatchers.Default) {
            backgroundColorList = backgroundColorList
                .map { it.copy(isSelected = false) }
                .toCollection(ArrayList())
            backgroundImageList.forEachIndexed { index, model ->
                model.isSelected = index == position
            }
        }
    }

    suspend fun updateBackgroundColorSelected(position: Int) {
        withContext(Dispatchers.Default) {
            backgroundImageList = backgroundImageList
                .map { it.copy(isSelected = false) }
                .toCollection(ArrayList())
            backgroundColorList.forEachIndexed { index, model ->
                model.isSelected = index == position
            }
        }
    }

    fun updateTextFontSelected(position: Int) {
        textFontList = textFontList
            .map { it.copy(isSelected = false) }
            .toCollection(ArrayList())
        textFontList.forEachIndexed { index, model ->
            model.isSelected = index == position
        }
    }

    fun updateTextColorSelected(position: Int) {
        textColorList = textColorList
            .map { it.copy(isSelected = false) }
            .toCollection(ArrayList())
        textColorList.forEachIndexed { index, model ->
            model.isSelected = index == position
        }
    }

    // ========== Draw helpers ==========

    fun updateCurrentCurrentDraw(draw: Draw) {
        currentDraw = draw
    }

    fun addDrawView(draw: Draw) {
        if (draw is DrawableDraw) {
            drawViewList.add(draw)
        }
    }

    fun deleteDrawView(draw: Draw) {
        drawViewList.removeIf { it == draw }
    }

    fun resetDraw() {
        drawViewList.clear()
        currentDraw = null
    }

    fun updatePathDefault(path: String) {
        pathDefault = path
    }

    // ========== Drawable / Emoji ==========

    fun loadDrawableEmoji(
        bitmap: Bitmap,
        isCharacter: Boolean = false,
        isText: Boolean = false
    ): DrawableDraw {
        val drawable = bitmap.toDrawable(context.resources)
        val timestamp = SimpleDateFormat("dd_MM_yyyy_hh_mm_ss").format(Date())
        val drawableEmoji = DrawableDraw(drawable, "$timestamp.png")
        drawableEmoji.isCharacter = isCharacter
        drawableEmoji.isText = isText
        return drawableEmoji
    }

    // ========== Cleanup ==========

    fun clearAllData() {
        backgroundImageList.clear()
        backgroundColorList.clear()
        stickerList.clear()
        speechList.clear()
        textFontList.clear()
        textColorList.clear()
        drawViewList.clear()
        currentDraw = null
        pathDefault = ""
    }
}