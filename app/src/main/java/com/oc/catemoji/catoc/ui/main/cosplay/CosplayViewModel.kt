package com.oc.catemoji.catoc.ui.main.cosplay

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CosplayViewModel @Inject constructor(
    private val appDataManager: AppDataManager
) : ViewModel() {
    // ✅ Khai báo HẾT property TRƯỚC init
    private val _randomItem = MutableStateFlow<RandomItem?>(null)
    val randomItem: StateFlow<RandomItem?> = _randomItem.asStateFlow()
    private val _isDataReady = MutableStateFlow(false)
    val isDataReady: StateFlow<Boolean> = _isDataReady.asStateFlow()
    private var _cachedBitmap: Bitmap? = null
    val cachedBitmap get() = _cachedBitmap
    init {
        viewModelScope.launch {
            appDataManager.templates
                .filter { it.isNotEmpty() }
                .take(1) // Chỉ trigger lần đầu
                .collect {   _isDataReady.value = true }
        }
    }


    data class RandomItem(
        val templateIndex: Int,
        val template     : CustomModel,
        val selections   : ArrayList<SelectionIndex>,
        val resolvedPaths: List<String?>
    )
    fun setCachedBitmap(bmp: Bitmap) { _cachedBitmap = bmp }
    override fun onCleared() {
        super.onCleared()
        _cachedBitmap?.recycle()
        _cachedBitmap = null
    }
    fun randomize(isOnline: Boolean = true) {
        if (_randomItem.value != null) {
            _cachedBitmap?.recycle()
            _cachedBitmap = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            val allTemplates = appDataManager.templates.value
            if (allTemplates.isEmpty()) return@launch

            val filtered = if (isOnline) allTemplates
            else allTemplates.filter { !it.id.startsWith("online_") }

            if (filtered.isEmpty()) return@launch

            // Tránh random lại đúng nhân vật vừa hiển thị khi vẫn còn lựa chọn khác.
            val previousTemplateId = _randomItem.value?.template?.id
            val templatePool = filtered
                .filterNot { it.id == previousTemplateId }
                .ifEmpty { filtered }
            val template = templatePool.random()
            // ✅ Lấy index từ allTemplates, không phải filtered
            val realIndex = allTemplates.indexOf(template)

            val sel   = randomSelections(template)
            val paths = resolvePaths(template, sel)

            _randomItem.value = RandomItem(
                templateIndex = realIndex,  // ✅
                template      = template,
                selections    = sel,
                resolvedPaths = paths
            )
        }
    }

    private fun randomSelections(template: CustomModel): ArrayList<SelectionIndex> {
        val list = ArrayList<SelectionIndex>()
        template.listPath.forEachIndexed { bpIdx, bp ->
            val colorCount = bp.listPath.size
            if (colorCount == 0) { list.add(SelectionIndex(bpIdx, 0, 0)); return@forEachIndexed }
            val colorIdx = (0 until colorCount).random()
            val color    = bp.listPath[colorIdx]
            val paths    = color.listPath
            // Random trên TOÀN BỘ data. Code cũ cắt nửa sau khi size > 6,
            // khiến rất nhiều item không bao giờ có cơ hội xuất hiện.
            val validIdx = paths.indices.filter {
                paths[it] != "none" && paths[it] != "dice"
            }
            val pathIdx = when {
                validIdx.isNotEmpty() -> validIdx.random()
                paths.isNotEmpty() -> paths.indices.random()
                else -> 0
            }
            list.add(SelectionIndex(bpIdx, colorIdx, pathIdx))
        }
        return list
    }

    private fun resolvePaths(template: CustomModel, sel: ArrayList<SelectionIndex>): List<String?> =
        template.listPath.mapIndexed { bpIdx, bp ->
            val s     = sel.getOrNull(bpIdx) ?: return@mapIndexed null
            val color = bp.listPath.getOrNull(s.colorIndex) ?: return@mapIndexed null
            val path  = color.listPath.getOrNull(s.pathIndex) ?: return@mapIndexed null
            if (path == "none") null else path
        }
}
