package com.oc.catemoji.catoc.core.extention

import android.content.Intent
import android.app.Activity

fun Activity.toSettingFromHome() {
    startActivity(Intent(this, com.oc.catemoji.catoc.ui.main.setting.SettingActivity::class.java))
}

private fun Activity.startOnboardingScreen(target: Class<out Activity>, firstRun: Boolean = false) {
    val intent = Intent(this, target)
    if (target == com.oc.catemoji.catoc.ui.language.LanguageActivity::class.java) {
        intent.putExtra(com.oc.catemoji.catoc.ui.language.LanguageActivity.EXTRA_FIRST_RUN, firstRun)
    }
    startActivity(intent)
    finish()
}

fun Activity.toLanguage() = startOnboardingScreen(
    com.oc.catemoji.catoc.ui.language.LanguageActivity::class.java,
    firstRun = true
)

fun Activity.toIntro() = startOnboardingScreen(
    com.oc.catemoji.catoc.ui.onboarding.intro.IntroActivity::class.java
)

fun Activity.toPermission() = startOnboardingScreen(
    com.oc.catemoji.catoc.ui.onboarding.permission.PermissionActivity::class.java
)

fun Activity.toHome() {
    startActivity(
        Intent(this, com.oc.catemoji.catoc.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(com.oc.catemoji.catoc.MainActivity.EXTRA_OPEN_HOME, true)
    )
}

fun Activity.toHomeFromPermission() = toHome()
fun Activity.toHomeFromLanguage() = finish()
fun Activity.toSettingFromLang() = finish()
fun Activity.toLangFromSetting() = startOnboardingScreen(
    com.oc.catemoji.catoc.ui.language.LanguageActivity::class.java
)
fun Activity.toIntroFromLanguage() = startOnboardingScreen(
    com.oc.catemoji.catoc.ui.onboarding.intro.IntroActivity::class.java
)
fun Activity.toHomeFromSetting() = toHome()
