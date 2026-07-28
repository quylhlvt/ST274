package com.oc.catemoji.catoc.core.extention

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
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
    // Edge-to-edge nhưng không dùng FLAG_LAYOUT_NO_LIMITS để adjustResize vẫn hoạt động.
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // Cho phép nội dung lấp đầy phần màn hình chứa tai thỏ/giọt nước (Android 9+).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    val layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    window.decorView.systemUiVisibility = layoutFlags

    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.isAppearanceLightStatusBars = isBlack
    insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insetsController.hide(WindowInsetsCompat.Type.systemBars())

    // Android 9: re-apply khi system tự reset (do focus/touch)
    window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
        if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0 ||
            visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
        ) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
fun Fragment.hideSoftKeyboard() {
    val inputMethodManager = requireContext()
        .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    inputMethodManager?.hideSoftInputFromWindow(view?.windowToken, 0)
}
