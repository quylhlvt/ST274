package com.oc.catemoji.catoc.ui.main.show

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import com.oc.catemoji.catoc.data.model.custom.ColorModel
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ShowViewModel @Inject constructor(
    private val appDataManager: AppDataManager
) :
    ViewModel() {

    private val _state = MutableStateFlow(ShowState())
    val state: StateFlow<ShowState> = _state.asStateFlow()

    /** Phát khi đạt 100% — Fragment lắng nghe để navigate */
    private val _onComplete = MutableSharedFlow<Unit>(replay = 0)
    val onComplete = _onComplete.asSharedFlow()


    // ── INIT — gọi từ Fragment sau khi nhận args từ CosplayFragment ───────────

    /**
     * [templateIndex]   : index template random từ CosplayViewModel
     * [targetSelections]: selections random từ CosplayViewModel (đáp án)
     */
    fun init(templateIndex: Int, targetSelections: ArrayList<SelectionIndex>) {

        if (_state.value.listData.isNotEmpty()) return  // safe với process death


        val template = appDataManager.getCharacterByIndex(templateIndex) ?: return
        val sorted   = template.listPath.sortedBy { it.zIndex }

        // Remap targetSelections từ unsorted sang sorted (giống CustomizeViewModel.initWithSelections)
        val remapped = sorted.mapIndexed { sortedIdx, bp ->
            val originalIdx = template.listPath.indexOf(bp)
            val sel = targetSelections.getOrElse(originalIdx) { SelectionIndex(originalIdx, 0, 0) }
            SelectionIndex(sortedIdx, sel.colorIndex, sel.pathIndex)
        }

        // User bắt đầu với default (index 0) — giống "tạo nhân vật mới"
        val userDefault = sorted.mapIndexed { i, _ ->
            if (i == 0) SelectionIndex(i, 0, 1) else SelectionIndex(i, 0, 0)
        }

        _state.value = ShowState(
            template         = template,
            listData         = sorted,
            targetSelections = remapped,
            userSelections   = userDefault,
            currentNavIndex  = 0,
            isLoading        = true,
            matchPercent     = calcPercent(sorted.size, remapped, userDefault)
        )
    }

    fun onLoadingComplete() = _state.update { it.copy(isLoading = false) }

    // ── USER INTERACTIONS (giống CustomizeViewModel) ──────────────────────────

    fun selectNav(navIndex: Int) = _state.update { it.copy(currentNavIndex = navIndex) }

    fun toggleFlip() = _state.update { it.copy(isFlipped = !it.isFlipped) }

    fun selectColor(colorIndex: Int) {
        updateUserSelection { state, old ->
            val bp        = state.listData.getOrNull(state.currentNavIndex) ?: return@updateUserSelection old
            val safeColor = colorIndex.coerceIn(0, bp.listPath.size - 1)
            val maxPath   = (bp.listPath.getOrNull(safeColor)?.listPath?.size ?: 1) - 1
            SelectionIndex(old.bodyPartIndex, safeColor, old.pathIndex.coerceIn(0, maxPath))
        }
    }

    fun selectPath(pathIndex: Int) {
        updateUserSelection { state, old ->
            val bp      = state.listData.getOrNull(state.currentNavIndex) ?: return@updateUserSelection old
            val maxPath = (bp.listPath.getOrNull(old.colorIndex)?.listPath?.size ?: 1) - 1
            SelectionIndex(old.bodyPartIndex, old.colorIndex, pathIndex.coerceIn(0, maxPath))
        }
    }

    fun selectNone() = selectPath(0)

    fun selectDiceCurrent() {
        updateUserSelection { state, old ->
            val bp    = state.listData.getOrNull(state.currentNavIndex) ?: return@updateUserSelection old
            val paths = bp.listPath.getOrNull(old.colorIndex)?.listPath ?: return@updateUserSelection old
            val start = startIndexAfterSpecial(paths)
            val idx   = if (paths.size > start) (start until paths.size).random() else start
            SelectionIndex(old.bodyPartIndex, old.colorIndex, idx)
        }
    }

    fun randomizeAll() {
        val state = _state.value
        val newSel = state.listData.mapIndexed { i, bp ->
            val colorIdx = if (bp.listPath.size > 1) (0 until bp.listPath.size).random() else 0
            val paths    = bp.listPath.getOrNull(colorIdx)?.listPath ?: emptyList()
            val start    = startIndexAfterSpecial(paths)
            val pathIdx  = if (paths.size > start) (start until paths.size).random() else start
            SelectionIndex(i, colorIdx, pathIdx)
        }
        val percent = calcPercent(state.listData.size, state.targetSelections, newSel)
        _state.update { it.copy(userSelections = newSel, matchPercent = percent) }
        checkComplete(percent)
    }

    fun resetAll() {
        val state   = _state.value
        val newSel  = state.listData.mapIndexed { i, _ ->
            if (i == 0) SelectionIndex(i, 0, 1) else SelectionIndex(i, 0, 0)
        }
        val percent = calcPercent(state.listData.size, state.targetSelections, newSel)
        _state.update { it.copy(userSelections = newSel, matchPercent = percent) }
    }

    // ── PATH RESOLUTION ───────────────────────────────────────────────────────

    /** Path theo userSelections — để render nhân vật user đang tạo */
    fun resolveUserPathAt(bodyPartIndex: Int): String? {
        val s    = _state.value
        val bp   = s.listData.getOrNull(bodyPartIndex) ?: return null
        val sel  = s.userSelections.getOrNull(bodyPartIndex) ?: return null
        val path = bp.listPath.getOrNull(sel.colorIndex)?.listPath?.getOrNull(sel.pathIndex) ?: return null
        return if (path == "none" || path == "dice") null else path
    }

    /** Path theo targetSelections — để tính % (không render lên màn) */
    fun resolveTargetPathAt(bodyPartIndex: Int): String? {
        val s    = _state.value
        val bp   = s.listData.getOrNull(bodyPartIndex) ?: return null
        val sel  = s.targetSelections.getOrNull(bodyPartIndex) ?: return null
        val path = bp.listPath.getOrNull(sel.colorIndex)?.listPath?.getOrNull(sel.pathIndex) ?: return null
        return if (path == "none" || path == "dice") null else path
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    /**
     * Tính % nav khớp: mỗi nav đúng cả colorIndex lẫn pathIndex = 100/total
     * Kết quả trả về Int 0..100
     */
    private fun calcPercent(
        total : Int,
        target: List<SelectionIndex>,
        user  : List<SelectionIndex>
    ): Int {
        if (total == 0) return 0
        val matched = (0 until total).count { i ->
            val t = target.getOrNull(i) ?: return@count false
            val u = user.getOrNull(i)   ?: return@count false
            t.colorIndex == u.colorIndex && t.pathIndex == u.pathIndex
        }
        return (matched * 100f / total).roundToInt()
    }

    private fun checkComplete(percent: Int) {
        if (percent >= 100) {
            viewModelScope.launch { _onComplete.emit(Unit) }
        }
    }

    private fun startIndexAfterSpecial(paths: List<String>): Int = when {
        paths.firstOrNull() == "none" -> 2
        paths.firstOrNull() == "dice" -> 1
        else -> 0
    }

    private fun updateUserSelection(
        transform: (ShowState, SelectionIndex) -> SelectionIndex
    ) {
        _state.update { state ->
            val navIdx  = state.currentNavIndex
            val old     = state.userSelections.getOrElse(navIdx) { SelectionIndex(navIdx, 0, 0) }
            val new     = transform(state, old)
            val updated = state.userSelections.toMutableList()
            if (navIdx < updated.size) updated[navIdx] = new
            else {
                while (updated.size < navIdx) updated.add(SelectionIndex(updated.size, 0, 0))
                updated.add(new)
            }
            val percent = calcPercent(state.listData.size, state.targetSelections, updated)
            state.copy(userSelections = updated, matchPercent = percent)
        }
        checkComplete(_state.value.matchPercent)
    }
    // ShowViewModel.kt
    fun reset() {
        _state.value = ShowState()
    }
}