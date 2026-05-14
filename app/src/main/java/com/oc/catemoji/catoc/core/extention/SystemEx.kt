package com.oc.catemoji.catoc.core.extention

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

fun Activity.hideNavigation(isBlack: Boolean = false) {
    // Bỏ FLAG_LAYOUT_NO_LIMITS — nó block keyboard detection
    // Thay bằng fitSystemWindows = false qua WindowCompat
    WindowCompat.setDecorFitsSystemWindows(window, false)

    val flags = if (isBlack) {
        (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    } else {
        (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    window.decorView.systemUiVisibility = flags

    // Android 9: re-apply khi system tự reset (do focus/touch)
    window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
        if (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
            window.decorView.systemUiVisibility = flags
        }
    }
}
fun Fragment.hideSoftKeyboard() {
    val inputMethodManager = requireContext()
        .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    inputMethodManager?.hideSoftInputFromWindow(view?.windowToken, 0)
}