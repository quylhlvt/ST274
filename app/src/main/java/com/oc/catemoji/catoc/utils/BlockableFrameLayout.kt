package com.oc.catemoji.catoc.utils

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout


class BlockableFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var isBlocked = false

    override fun addView(child: android.view.View?) {
        if (isBlocked) return
        super.addView(child)
    }

    override fun addView(child: android.view.View?, index: Int) {
        if (isBlocked) return
        super.addView(child, index)
    }

    override fun addView(child: android.view.View?, params: android.view.ViewGroup.LayoutParams?) {
        if (isBlocked) return
        super.addView(child, params)
    }
}