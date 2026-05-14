package com.oc.catemoji.catoc.ui.main.show

import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.ColorModel
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
data class ShowState(
    val template        : CustomModel?         = null,
    val listData        : List<BodyPartModel>  = emptyList(),
    /** Selections của nhân vật random từ Cosplay — đây là ĐÁP ÁN */
    val targetSelections: List<SelectionIndex> = emptyList(),
    /** Selections user đang chỉnh — giống CustomizeState.selections */
    val userSelections  : List<SelectionIndex> = emptyList(),
    val currentNavIndex : Int     = 0,
    val isFlipped       : Boolean = false,
    val isLoading       : Boolean = true,
    val matchPercent    : Int     = 0
) {
    val currentColors: List<ColorModel>
        get() = listData.getOrNull(currentNavIndex)?.listPath ?: emptyList()
    val currentPaths: List<String>
        get() {
            val sel = userSelections.getOrNull(currentNavIndex) ?: return emptyList()
            return listData.getOrNull(currentNavIndex)
                ?.listPath?.getOrNull(sel.colorIndex)?.listPath ?: emptyList()
        }
    val currentColorIndex: Int    get() = userSelections.getOrNull(currentNavIndex)?.colorIndex ?: 0
    val currentPathIndex : Int    get() = userSelections.getOrNull(currentNavIndex)?.pathIndex  ?: 0
    val hasMultipleColors: Boolean get() = (listData.getOrNull(currentNavIndex)?.listPath?.size ?: 0) > 1
}