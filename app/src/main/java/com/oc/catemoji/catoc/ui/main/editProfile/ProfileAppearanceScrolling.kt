package com.oc.catemoji.catoc.ui.main.editProfile

import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView

/** Centre the initial selection; LinearLayoutManager clamps the first/last items to the edges. */
internal fun RecyclerView.revealProfileSelection(position: Int) {
    if (position < 0) return
    doOnLayout {
        val manager = layoutManager as? LinearLayoutManager ?: return@doOnLayout
        if (position >= (adapter?.itemCount ?: 0)) return@doOnLayout
        // Avatar and frame options have a uniform width, including their item margins.
        val item = manager.findViewByPosition(position) ?: manager.getChildAt(0) ?: return@doOnLayout
        val horizontal = OrientationHelper.createHorizontalHelper(manager)
        val offset = ((horizontal.totalSpace - horizontal.getDecoratedMeasurement(item)) / 2)
            .coerceAtLeast(0)
        manager.scrollToPositionWithOffset(position, offset)
    }
}
