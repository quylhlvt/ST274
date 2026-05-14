package com.oc.catemoji.catoc.core.helper

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LanguageHelper {

    private var myLocale: Locale? = null

    fun setLocale(context: Context) {
        val language = SharedPreferencesManager.isLanguageKey()
        if (language.isEmpty()) {
            val config = Configuration()
            val locale = Locale.getDefault()
            Locale.setDefault(locale)
            config.setLocale(locale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        } else {
            changeLang(language, context)
        }
    }

    fun changeLang(lang: String, context: Context) {
        if (lang.equals("", ignoreCase = true)) return
       myLocale = Locale(lang)
        saveLocale( lang)
        Locale.setDefault(myLocale!!)
        val config = Configuration()
        config.setLocale(myLocale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun saveLocale( lang: String) {
        SharedPreferencesManager.setLanguageKey(lang)
    }


    /**
     * Cập nhật ngôn ngữ KHÔNG restart
     */
//    fun setLocale(context: Context, languageCode: String) {
//        val locale = Locale(languageCode)
//        Locale.setDefault(locale)
//
//        val resources: Resources = context.resources
//        val config: Configuration = resources.configuration
//        config.setLocale(locale)
//
//        resources.updateConfiguration(config, resources.displayMetrics)
//    }
// LanguageHelper — chỉ giữ lại hàm này, xóa các hàm còn lại
    fun setLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        SharedPreferencesManager.setLanguageKey(languageCode)

        // ✅ Update configuration ngay lập tức
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}