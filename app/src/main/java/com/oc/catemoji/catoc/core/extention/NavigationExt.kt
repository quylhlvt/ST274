package com.oc.catemoji.catoc.core.extention

import android.app.Activity
import android.content.Intent
import com.oc.catemoji.catoc.ui.language.LanguageActivity
import com.oc.catemoji.catoc.ui.main.home.HomeActivity
import com.oc.catemoji.catoc.ui.main.setting.SettingActivity
import com.oc.catemoji.catoc.ui.onboarding.intro.IntroActivity
import com.oc.catemoji.catoc.ui.onboarding.permission.PermissionActivity

fun Activity.toSettingFromHome() {
    startActivity(Intent(this, SettingActivity::class.java))
}

private fun Activity.startOnboardingScreen(target: Class<out Activity>) {
    startActivity(Intent(this, target))
    finish()
}

fun Activity.toLanguage() = startOnboardingScreen(LanguageActivity::class.java)
fun Activity.toIntro() = startOnboardingScreen(IntroActivity::class.java)
fun Activity.toPermission() = startOnboardingScreen(PermissionActivity::class.java)

fun Activity.toHome() {
    startActivity(Intent(this, HomeActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    })
    finish()
}

/** Khởi tạo Home bằng context mới sau khi đổi locale và loại Settings khỏi back stack. */
fun Activity.toHomeAfterLanguageChange() {
    startActivity(Intent(this, HomeActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    })
    finish()
}

fun Activity.toHomeFromPermission() = toHome()
fun Activity.toHomeFromLanguage() = finish()
fun Activity.toSettingFromLang() = finish()
fun Activity.toLangFromSetting() {
    startActivity(Intent(this, LanguageActivity::class.java).apply {
        putExtra(LanguageActivity.EXTRA_FROM_SETTING, true)
    })
}
fun Activity.toIntroFromLanguage() = startOnboardingScreen(IntroActivity::class.java)
fun Activity.toHomeFromSetting() = toHome()
