package com.oc.catemoji.catoc.ui.main.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.ColorModel
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI STATE ──────────────────────────────────────────────────────────────────

data class CustomizeState(
    val template:       CustomModel?        = null,
    val listData:       List<BodyPartModel> = emptyList(),

    /**
     * ✅ KEY: CHỈ LƯU INDEX – không lưu path string.
     * Path được resolve on-demand từ listData + selections.
     */
    val selections:     List<SelectionIndex> = emptyList(),
    val currentNavIndex: Int     = 0,
    val isFlipped:       Boolean = false,
    val isLoading:       Boolean = true,
    val isSaving:        Boolean = false,
    val savedImagePath:  String? = null,
    val randomCount:     Int     = 0,
    val error:           String? = null
) {
    val currentColors: List<ColorModel>
        get() = listData.getOrNull(currentNavIndex)?.listPath ?: emptyList()

    val currentPaths: List<String>
        get() {
            val sel = selections.getOrNull(currentNavIndex) ?: return emptyList()
            return listData.getOrNull(currentNavIndex)
                ?.listPath?.getOrNull(sel.colorIndex)?.listPath ?: emptyList()
        }

    val currentColorIndex: Int get() = selections.getOrNull(currentNavIndex)?.colorIndex ?: 0
    val currentPathIndex:  Int get() = selections.getOrNull(currentNavIndex)?.pathIndex ?: 0
    val hasMultipleColors: Boolean get() = (listData.getOrNull(currentNavIndex)?.listPath?.size ?: 0) > 1
}

// ── VIEWMODEL ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CustomizeViewModel @Inject constructor(
    private val appDataManager: AppDataManager
) : ViewModel() {
    private var editingCustomizedId: String? = null

    private val _state = MutableStateFlow(CustomizeState())
    val state: StateFlow<CustomizeState> = _state.asStateFlow()
    private var isInitialized = false

    // ── INIT ──────────────────────────────────────────────────────────────────

    /** Khởi tạo với template mới (không có saved selections). */
    fun initNew(templateIndex: Int) {
        // ✅ Dùng state check thay vì flag — safe với process death
        if (_state.value.listData.isNotEmpty()) return
        editingCustomizedId = null
        val template = appDataManager.getCharacterByIndex(templateIndex) ?: return
        val sorted   = sortBodyParts(template.listPath)
        _state.value = CustomizeState(
            template        = template,
            listData        = sorted,
            selections      = buildDefaultSelections(sorted),
            currentNavIndex = 0,
            isLoading       = true
        )
    }

    /** Khởi tạo để EDIT character đã lưu. */
    fun initEdit(templateIndex: Int, savedSelections: List<SelectionIndex>, isFlipped: Boolean) {
        if (_state.value.listData.isNotEmpty()) return
        val template = appDataManager.getCharacterByIndex(templateIndex) ?: return
        val sorted   = sortBodyParts(template.listPath)
        _state.value = CustomizeState(
            template        = template,
            listData        = sorted,
            selections      = clampSelections(sorted, savedSelections),
            isFlipped       = isFlipped,
            currentNavIndex = 0,
            isLoading       = true
        )
    }
    // CustomizeViewModel.kt — thêm hàm initWithSelections
// Xóa hàm initWithSelections sai đi, thay bằng:
    fun initWithSelections(templateIndex: Int, savedSelections: ArrayList<SelectionIndex>) {
        if (_state.value.listData.isNotEmpty()) return
        val template = appDataManager.getCharacterByIndex(templateIndex) ?: return
        val sorted   = sortBodyParts(template.listPath)
        val remapped = sorted.mapIndexed { sortedIdx, bp ->
            val originalIdx = template.listPath.indexOf(bp)
            val sel = savedSelections.getOrElse(originalIdx) { SelectionIndex(originalIdx, 0, 0) }
            SelectionIndex(sortedIdx, sel.colorIndex, sel.pathIndex)
        }
        _state.value = CustomizeState(
            template        = template,
            listData        = sorted,
            selections      = clampSelections(sorted, remapped),
            isFlipped       = false,
            currentNavIndex = 0,
            isLoading       = true
        )
    }
    fun initEditWithCustomizedId(
        templateIndex: Int,
        customizedId: String,
        savedSelections: List<SelectionIndex>,
        isFlipped: Boolean
    ) {
        if (_state.value.listData.isNotEmpty()) return
        editingCustomizedId = customizedId
        val template = appDataManager.getCharacterByIndex(templateIndex) ?: return
        val sorted   = sortBodyParts(template.listPath)
        _state.value = CustomizeState(
            template        = template,
            listData        = sorted,
            selections      = clampSelections(sorted, savedSelections),
            isFlipped       = isFlipped,
            currentNavIndex = 0,
            isLoading       = true
        )
    }
    fun onLoadingComplete() = _state.update { it.copy(isLoading = false) }

    // ── SELECTIONS ────────────────────────────────────────────────────────────

    fun selectNav(navIndex: Int)   = _state.update { it.copy(currentNavIndex = navIndex) }
    fun toggleFlip()               = _state.update { it.copy(isFlipped = !it.isFlipped) }

    fun selectColor(colorIndex: Int) {
        updateSelection { state, old ->
            val bp        = state.listData.getOrNull(state.currentNavIndex) ?: return@updateSelection old
            val safeColor = colorIndex.coerceIn(0, bp.listPath.size - 1)
            val maxPath   = (bp.listPath.getOrNull(safeColor)?.listPath?.size ?: 1) - 1
            SelectionIndex(old.bodyPartIndex, safeColor, old.pathIndex.coerceIn(0, maxPath))
        }
    }

    fun selectPath(pathIndex: Int) {
        updateSelection { state, old ->
            val bp      = state.listData.getOrNull(state.currentNavIndex) ?: return@updateSelection old
            val maxPath = (bp.listPath.getOrNull(old.colorIndex)?.listPath?.size ?: 1) - 1
            SelectionIndex(old.bodyPartIndex, old.colorIndex, pathIndex.coerceIn(0, maxPath))
        }
    }

    fun selectNone() = selectPath(0)

    fun selectDiceCurrent() {
        updateSelection { state, old ->
            val bp    = state.listData.getOrNull(state.currentNavIndex) ?: return@updateSelection old
            val paths = bp.listPath.getOrNull(old.colorIndex)?.listPath ?: return@updateSelection old
            val start = startIndexAfterSpecial(paths)
            val idx   = if (paths.size > start) (start until paths.size).random() else start
            SelectionIndex(old.bodyPartIndex, old.colorIndex, idx)
        }
    }

    fun randomizeAll() {
        val state = _state.value
        val newSelections = state.listData.mapIndexed { i, bp ->
            val colorIdx = if (bp.listPath.size > 1) (0 until bp.listPath.size).random() else 0
            val paths    = bp.listPath.getOrNull(colorIdx)?.listPath ?: emptyList()
            val start    = startIndexAfterSpecial(paths)
            val pathIdx  = if (paths.size > start) (start until paths.size).random() else start
            SelectionIndex(i, colorIdx, pathIdx)
        }
        _state.update { it.copy(selections = newSelections, randomCount = it.randomCount + 1) }
    }

    fun resetAll() = _state.update {
        it.copy(
            selections = buildDefaultSelections(it.listData),
            isFlipped = false,        // ← reset flip
            currentNavIndex = 0       // ← reset về nav đầu
        )
    }

    // ── PATH RESOLUTION ───────────────────────────────────────────────────────

    /** Lấy path thực tế tại bodyPartIndex. null = "none" hoặc "dice". */
    fun resolvePathAt(bodyPartIndex: Int): String? {
        val s    = _state.value
        val bp   = s.listData.getOrNull(bodyPartIndex) ?: return null
        val sel  = s.selections.getOrNull(bodyPartIndex) ?: return null
        val path = bp.listPath.getOrNull(sel.colorIndex)?.listPath?.getOrNull(sel.pathIndex) ?: return null
        return if (path == "none" || path == "dice") null else path
    }

    /** Resolve tất cả paths theo zIndex để render bitmap. */
    fun resolveAllCurrentPaths(): List<Pair<Int, String>> {
        val s = _state.value
        return s.listData.mapIndexedNotNull { i, bp ->
            val sel  = s.selections.getOrNull(i) ?: return@mapIndexedNotNull null
            val path = bp.listPath.getOrNull(sel.colorIndex)?.listPath?.getOrNull(sel.pathIndex)
                ?: return@mapIndexedNotNull null
            if (path == "none" || path == "dice") null else bp.zIndex to path
        }.sortedBy { it.first }
    }

    /** Lấy selections hiện tại để persist. ✅ Chỉ là danh sách index. */
    fun getCurrentSelections(): ArrayList<SelectionIndex> = ArrayList(_state.value.selections)

    // ── SAVE ──────────────────────────────────────────────────────────────────

    fun onSaveComplete(renderedImagePath: String): Pair<CustomModel, List<SelectionIndex>>? {
        val state    = _state.value
        val template = state.template ?: return null
        _state.update { it.copy(savedImagePath = renderedImagePath, isSaving = false) }

        val characterToSave = editingCustomizedId
            ?.let { id -> appDataManager.getCharacterById(id) }
            ?.copy(
                selections = ArrayList(state.selections),
                imageSave  = renderedImagePath,
                isFlipped  = state.isFlipped,
                updatedAt  = System.currentTimeMillis()
            )
            ?: template  // tạo mới từ template

        return characterToSave to state.selections
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private fun sortBodyParts(parts: List<BodyPartModel>) = parts.sortedBy { it.zIndex }

    private fun buildDefaultSelections(parts: List<BodyPartModel>): List<SelectionIndex> =
        parts.mapIndexed { i, _ -> if (i == 0) SelectionIndex(i, 0, 1) else SelectionIndex(i, 0, 0) }

    private fun clampSelections(parts: List<BodyPartModel>, saved: List<SelectionIndex>): List<SelectionIndex> =
        parts.mapIndexed { i, bp ->
            val s         = saved.getOrElse(i) { SelectionIndex(i, 0, 0) }
            val safeColor = s.colorIndex.coerceIn(0, maxOf(0, bp.listPath.size - 1))
            val maxPath   = maxOf(0, (bp.listPath.getOrNull(safeColor)?.listPath?.size ?: 1) - 1)
            SelectionIndex(i, safeColor, s.pathIndex.coerceIn(0, maxPath))
        }

    private fun startIndexAfterSpecial(paths: List<String>): Int = when {
        paths.firstOrNull() == "none" -> 2
        paths.firstOrNull() == "dice" -> 1
        else -> 0
    }

    private fun updateSelection(transform: (CustomizeState, SelectionIndex) -> SelectionIndex) {
        _state.update { state ->
            val navIdx  = state.currentNavIndex
            val old     = state.selections.getOrElse(navIdx) { SelectionIndex(navIdx, 0, 0) }
            val new     = transform(state, old)
            val updated = state.selections.toMutableList()
            if (navIdx < updated.size) updated[navIdx] = new
            else {
                while (updated.size < navIdx) updated.add(SelectionIndex(updated.size, 0, 0))
                updated.add(new)
            }
            state.copy(selections = updated)
        }
    }
}