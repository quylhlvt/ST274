package com.oc.catemoji.catoc.ui.main.studio

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@Parcelize
data class StudioDraftItem(
    val path: String,
    val matrixValues: FloatArray
) : Parcelable

data class CatStudioState(
    val listData: List<BodyPartModel> = emptyList(),
    val currentNavIndex: Int = 0,
    val selectedPath: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentPaths: List<String>
        get() = listData.getOrNull(currentNavIndex)
            ?.listPath
            ?.firstOrNull()
            ?.listPath
            .orEmpty()
}

@HiltViewModel
class CatStudioViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(CatStudioState())
    val state: StateFlow<CatStudioState> = _state.asStateFlow()

    val drawItems = arrayListOf<DrawableDraw>()
    private val pathToLocation = HashMap<String, PathLocation>()
    var currentDraw: DrawableDraw? = null
        private set
    var navScrollState: Parcelable? = null
    var partScrollState: Parcelable? = null
    val draftItems: List<StudioDraftItem>
        get() = savedStateHandle[KEY_DRAFT_ITEMS] ?: emptyList()

    /**
     * Mỗi instance Studio chỉ tự mở video một lần. ViewModel vẫn được giữ khi
    * navigate sang AddCharacter rồi quay lại nên dialog sẽ không tự hiện lại.
     */
    fun shouldShowInitialGuide(): Boolean {
        if (savedStateHandle.get<Boolean>(KEY_INITIAL_GUIDE_SHOWN) == true) return false
        savedStateHandle[KEY_INITIAL_GUIDE_SHOWN] = true
        return true
    }

    fun setCatalogue(parts: List<BodyPartModel>) {
        if (parts.isEmpty() || _state.value.listData === parts) return
        pathToLocation.clear()
        parts.forEachIndexed { navIndex, bodyPart ->
            bodyPart.listPath.forEach { color ->
                color.listPath.forEachIndexed { pathIndex, path ->
                    pathToLocation[path] = PathLocation(navIndex, pathIndex)
                }
            }
        }
        _state.update {
            it.copy(
                listData = parts,
                currentNavIndex = it.currentNavIndex.coerceIn(parts.indices),
                isLoading = false,
                error = null
            )
        }
    }

    fun selectNav(index: Int) {
        if (index !in _state.value.listData.indices) return
        _state.update { it.copy(currentNavIndex = index) }
    }

    fun selectPath(path: String) {
        _state.update { it.copy(selectedPath = path) }
    }

    fun saveDrawItems(items: List<DrawableDraw>) {
        drawItems.clear()
        drawItems.addAll(items)
        savedStateHandle[KEY_DRAFT_ITEMS] = ArrayList(items.map { draw ->
            StudioDraftItem(
                path = draw.drawablePath,
                matrixValues = FloatArray(9).also { values ->
                    draw.getMatrix().getValues(values)
                }
            )
        })
    }

    fun addDraw(draw: DrawableDraw) {
        if (drawItems.none { it === draw }) drawItems.add(draw)
        currentDraw = draw
    }

    fun removeDraw(draw: DrawableDraw): Boolean {
        drawItems.remove(draw)
        if (currentDraw === draw) currentDraw = null
        val shouldClearLayerFocus = _state.value.selectedPath == draw.drawablePath
        if (shouldClearLayerFocus) {
            _state.update { it.copy(selectedPath = null) }
        }
        return shouldClearLayerFocus
    }

    fun selectDraw(draw: DrawableDraw): PathLocation? {
        currentDraw = draw
        val path = draw.drawablePath
        val location = pathToLocation[path] ?: return null
        _state.update {
            it.copy(
                currentNavIndex = location.navIndex,
                selectedPath = path
            )
        }
        return location
    }

    fun clearCurrentDraw() {
        currentDraw = null
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    data class PathLocation(
        val navIndex: Int,
        val pathIndex: Int
    )

    companion object {
        private const val KEY_DRAFT_ITEMS = "studio_draft_items"
        private const val KEY_INITIAL_GUIDE_SHOWN = "studio_initial_guide_shown"
    }
}
