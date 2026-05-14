package com.oc.catemoji.catoc.core.extention

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.oc.catemoji.catoc.R

// ── Base navigate helpers ─────────────────────────────────────────────────────

fun Fragment.nav(actionId: Int) {
    try {
        findNavController().navigate(actionId)
    } catch (e: Exception) {
        // Đã ở đích hoặc action không tồn tại → ignore
    }
}

/**
 * Navigate an toàn với args — không crash khi:
 * - Action không tồn tại ở destination hiện tại (double-tap, wrong destination)
 */
fun NavController.safeNavigate(actionId: Int, args: Bundle? = null) {
    try {
        currentDestination?.getAction(actionId) ?: run {
            android.util.Log.w("SafeNav", "⚠️ Action $actionId not found from [${currentDestination?.label}]. Skipped.")
            return
        }
        navigate(actionId, args)
    } catch (e: IllegalArgumentException) {
        android.util.Log.e("SafeNav", "❌ ${e.message}")
    }
}

// ── Splash ────────────────────────────────────────────────────────────────────

fun Fragment.toLanguage() = nav(R.id.action_splash_to_language)
fun Fragment.toIntro()    = nav(R.id.action_splash_to_intro)

// ── Intro ─────────────────────────────────────────────────────────────────────

fun Fragment.toPermission() = nav(R.id.action_intro_to_permission)
fun Fragment.toHome()       = nav(R.id.action_intro_to_home)

// ── Permission ────────────────────────────────────────────────────────────────

fun Fragment.toHomeFromPermission() = nav(R.id.action_permission_to_home)

// ── Setting ───────────────────────────────────────────────────────────────────
fun Fragment.toLangFromSetting() = nav(R.id.action_setting_to_language)

fun Fragment.toHomeFromSetting() = nav(R.id.action_setting_to_home)

// ── Language ──────────────────────────────────────────────────────────────────
fun Fragment.toSettingFromLang() {
    try {
        // Pop back về Setting
        findNavController().popBackStack()
    } catch (e: Exception) {
        android.util.Log.e("Navigation", "Error popping back to setting: ${e.message}")
    }
}
fun Fragment.toIntroFromLanguage() = nav(R.id.action_language_to_intro)
fun Fragment.toHomeFromLanguage()  = nav(R.id.action_language_to_home)
// Home
fun Fragment.toSettingFromHome() = nav(R.id.action_home_to_setting)



fun Fragment.popBack(): Boolean {
    return try {
        findNavController().popBackStack()
    } catch (e: Exception) {
        android.util.Log.e("Navigation", "Error popping back: ${e.message}")
        false
    }
}