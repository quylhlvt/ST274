package com.oc.catemoji.catoc.core.extention

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat

fun Activity.hideNavigation(isBlack: Boolean = false) {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    val flags = if (isBlack) {
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    } else {
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    window.decorView.systemUiVisibility = flags
    window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
        if (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
            window.decorView.systemUiVisibility = flags
        }
    }
}

fun Activity.hideSoftKeyboard() {
    val inputMethodManager =
        getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    inputMethodManager?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
}
