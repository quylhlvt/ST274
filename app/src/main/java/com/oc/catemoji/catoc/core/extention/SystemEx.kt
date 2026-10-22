package com.oc.catemoji.catoc.core.extention

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Activity.hideNavigation(isBlack: Boolean = false) {
    window.hideNavigation(isBlack)
}

fun Dialog.hideNavigation(isBlack: Boolean = false) {
    window?.hideNavigation(isBlack)
}

private fun Window.hideNavigation(isBlack: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(this, false)

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

    val insetsController = WindowInsetsControllerCompat(this, decorView).apply {
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        isAppearanceLightStatusBars = isBlack
        hide(WindowInsetsCompat.Type.navigationBars())
    }
    decorView.systemUiVisibility = flags
    decorView.setOnSystemUiVisibilityChangeListener { visibility ->
        if (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
            decorView.systemUiVisibility = flags
            insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}

fun Activity.hideSoftKeyboard() {
    val inputMethodManager =
        getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    inputMethodManager?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
}
