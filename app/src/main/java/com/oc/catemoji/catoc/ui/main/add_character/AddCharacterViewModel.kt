package com.oc.catemoji.catoc.ui.main.add_character

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
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
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ========== Init guard ==========
    var isInitialized = false
    var isRestoringDraws = false
    var hasLoadedData = false
    var isPickingImage = false
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
    private val _backgroundImagePath = MutableStateFlow(
        savedStateHandle.get<String>(KEY_BACKGROUND_IMAGE_PATH)
    )
    val backgroundImagePath: StateFlow<String?> = _backgroundImagePath.asStateFlow()

    var savedBackgroundColor: Int?
        get() = savedStateHandle.get<Int>(KEY_BACKGROUND_COLOR)
        set(value) {
            if (value == null) savedStateHandle.remove<Int>(KEY_BACKGROUND_COLOR)
            else savedStateHandle[KEY_BACKGROUND_COLOR] = value
        }

    // ========== Tab state ==========
    // Chỉ dùng để biết tab nào đang active — KHÔNG dùng để control layout
    var isTextTabActive: Boolean = false
    var isSpeechDialogOpen: Boolean = false

    // ========== Draw state ==========
    var currentDraw: Draw? = null
    var drawViewList: ArrayList<DrawableDraw> = arrayListOf()

    // ========== Misc ==========
    var pathDefault = ""

    init {
        restoreCachedLists()
    }

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
        if (path == null) savedStateHandle.remove<String>(KEY_BACKGROUND_IMAGE_PATH)
        else savedStateHandle[KEY_BACKGROUND_IMAGE_PATH] = path
    }

    // ========== Data loading ==========

    fun loadDataFromMainViewModel(
        backgrounds: List<String>,
        stickers: List<String>,
        speeches: List<String>
    ) {
        // Giữ nguyên các list và trạng thái selected khi Fragment tạm dừng
        // (ví dụ lúc mở Photo Picker).
        if (hasLoadedData) return
        if (backgrounds.isEmpty() || stickers.isEmpty() || speeches.isEmpty()) return

        backgroundImageList.clear()
        backgroundImageList.add(SelectedAddModel(path = "")) // ← giữ item pick từ gallery
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

        hasLoadedData = true
        cacheLists()
    }
    fun loadDataFromQuantity(
        bgQuantity: Int,
        stickerQuantity: Int,
        bgBaseUrl: String,
        speeches: List<String>
    ) {
        val bgs = (1..bgQuantity).map { "$bgBaseUrl/Background/$it.png" }
        val stickers = (1..stickerQuantity).map { "$bgBaseUrl/Sticker/$it.png" }
        loadDataFromMainViewModel(bgs, stickers, speeches)
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
        savedStateHandle[KEY_SELECTED_BACKGROUND] = position
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
        savedStateHandle[KEY_SELECTED_BACKGROUND] = -1
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
        hasLoadedData = false
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
        hasLoadedData = false
        isInitialized = false
        currentDraw = null
        pathDefault = ""
        isPickingImage = false
        setBackgroundImage(null)
        savedBackgroundColor = null
        savedStateHandle.remove<ArrayList<String>>(KEY_BACKGROUND_PATHS)
        savedStateHandle.remove<ArrayList<String>>(KEY_STICKER_PATHS)
        savedStateHandle.remove<ArrayList<String>>(KEY_SPEECH_PATHS)
        savedStateHandle.remove<Int>(KEY_SELECTED_BACKGROUND)
        savedStateHandle.remove<String>(KEY_BACKGROUND_IMAGE_PATH)
        savedStateHandle.remove<Int>(KEY_BACKGROUND_COLOR)
    }

    private fun cacheLists() {
        savedStateHandle[KEY_BACKGROUND_PATHS] =
            ArrayList(backgroundImageList.map { it.path })
        savedStateHandle[KEY_STICKER_PATHS] =
            ArrayList(stickerList.map { it.path })
        savedStateHandle[KEY_SPEECH_PATHS] =
            ArrayList(speechList.map { it.path })
    }

    private fun restoreCachedLists() {
        val backgrounds = savedStateHandle
            .get<ArrayList<String>>(KEY_BACKGROUND_PATHS)
            .orEmpty()
        val stickers = savedStateHandle
            .get<ArrayList<String>>(KEY_STICKER_PATHS)
            .orEmpty()
        val speeches = savedStateHandle
            .get<ArrayList<String>>(KEY_SPEECH_PATHS)
            .orEmpty()

        if (backgrounds.isEmpty() || stickers.isEmpty() || speeches.isEmpty()) return

        val selectedBackground = savedStateHandle.get<Int>(KEY_SELECTED_BACKGROUND) ?: -1
        backgroundImageList.addAll(backgrounds.mapIndexed { index, path ->
            SelectedAddModel(path = path, isSelected = index == selectedBackground)
        })
        stickerList.addAll(stickers.map { SelectedAddModel(path = it) })
        speechList.addAll(speeches.map { SelectedAddModel(path = it) })
        backgroundColorList.addAll(DataLocal.getBackgroundColorDefault(context))
        textFontList.addAll(DataLocal.getTextFontDefault())
        textColorList.addAll(DataLocal.getTextColorDefault(context))
        hasLoadedData = true
    }

    private companion object {
        const val KEY_BACKGROUND_PATHS = "add_character_background_paths"
        const val KEY_STICKER_PATHS = "add_character_sticker_paths"
        const val KEY_SPEECH_PATHS = "add_character_speech_paths"
        const val KEY_SELECTED_BACKGROUND = "add_character_selected_background"
        const val KEY_BACKGROUND_IMAGE_PATH = "add_character_background_image_path"
        const val KEY_BACKGROUND_COLOR = "add_character_background_color"
    }
}
