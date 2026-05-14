package com.oc.catemoji.catoc.core.custom.listener

import android.R
import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver

class KeyboardListener(
    activity: Activity,
    private val onKeyboardVisibilityChanged: (isVisible: Boolean, keyboardHeight: Int) -> Unit
) : ViewTreeObserver.OnGlobalLayoutListener {

    private val rootView: View = activity.findViewById(R.id.content)
    private var lastVisible = false

    init {
        rootView.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onGlobalLayout() {
        val rect = Rect()
        rootView.getWindowVisibleDisplayFrame(rect)

        val rootHeight = rootView.rootView.height
        val keyboardHeight = rootHeight - rect.bottom
        val isVisible = keyboardHeight > rootHeight * 0.15

        if (isVisible != lastVisible) {
            lastVisible = isVisible
            onKeyboardVisibilityChanged(isVisible, keyboardHeight)
        }
    }

    fun detach() {
        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }
}