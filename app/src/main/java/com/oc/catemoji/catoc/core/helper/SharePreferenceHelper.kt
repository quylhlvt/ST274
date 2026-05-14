package com.oc.catemoji.catoc.core.helper

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Bundle


import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Singleton
import androidx.core.content.edit


@Singleton
object SharedPreferencesManager {
    lateinit var sharedPreferences: SharedPreferences
    lateinit var editor: Editor
    lateinit var bundle: Bundle
    private const val SPLASH_SCREEN = "splash_screen"
    private const val LANGUAGE_SCREEN = "language_screen"
    private const val LANGUAGE_KEY = "language_key"
    private const val PERMISSION_SCREEN = "permission_screen"
    private const val PERMISSION_NOTIFICATION = "NOTIFICATION_KEY"
    private const val PERMISSION_STORAGE = "STORAGE_KEY"
    private const val PERMISSION_CAMERA = "CAMERA_KEY"
    private const val COUNT_BACK_KEY = "COUNT_BACK_KEY"
    private const val RATE_KEY = "RATE_KEY"
    private const val VERSION_CODE_KEY = "version_code_key"
    fun clearAll() {
        // Chỉ giữ lại language CODE
        val savedLanguage = isLanguageKey()

        // Xóa toàn bộ
        sharedPreferences.edit(commit = true) { clear() }

        // Restore language code thôi, KHÔNG restore LANGUAGE_SCREEN
        if (savedLanguage.isNotEmpty()) {
            setLanguageKey(savedLanguage)
        }
        // ❌ Bỏ: if (savedLanguageScreen) { setLanuageScreen(true) }
    }
    fun getVersionCode(): Int = getIntDataByKey(VERSION_CODE_KEY)

    fun setVersionCode(versionCode: Int) {
        saveIntDataByKey(VERSION_CODE_KEY, versionCode)
    }
    fun isLanguageKey(): String=
        getStringDataByKey(
            LANGUAGE_KEY
        )

    fun setLanguageKey(isSkipped: String) {
        saveStringDataByKey(LANGUAGE_KEY, isSkipped)
    }

    fun isLanuageScreen(): Boolean = getBooleanDataByKey(LANGUAGE_SCREEN)
    fun setLanuageScreen(isSkipped: Boolean=true) {
      saveBooleanDataByKey(
            LANGUAGE_SCREEN,
            isSkipped
        )
    }

    fun isPermissionScreen(): Boolean = getBooleanDataByKey(PERMISSION_SCREEN)
    fun setPermissionScreen(isSkipped: Boolean) {
        saveBooleanDataByKey(PERMISSION_SCREEN, isSkipped)
    }

    fun isPermissionNotiRequest(): Int = getIntDataByKey(PERMISSION_NOTIFICATION)
    fun setPermissionNotiRequest(isSkipped: Int) {
        saveIntDataByKey(PERMISSION_NOTIFICATION, isSkipped)
    }
    fun isPermissionStorRequest(): Int = getIntDataByKey(PERMISSION_STORAGE)
    fun setPermissionStorRequest(isSkipped: Int) {
        saveIntDataByKey(PERMISSION_STORAGE, isSkipped)
    }
    fun isPermissionCamRequest(): Int = getIntDataByKey(PERMISSION_CAMERA)
    fun setPermissionCamRequest(isSkipped: Int) {
        saveIntDataByKey(PERMISSION_CAMERA, isSkipped)
    }

    fun isRateRequest(): Boolean = getBooleanDataByKey(RATE_KEY)
    fun setRateRequest(isSkipped: Boolean) {
        saveBooleanDataByKey(RATE_KEY, isSkipped)
    }

    fun isBackRequest(): Int = getIntDataByKey(COUNT_BACK_KEY)
    fun setBackRequest(isSkipped: Int) {
        saveIntDataByKey(COUNT_BACK_KEY, isSkipped)
    }




    private fun getBooleanDataByKey(key: String?): Boolean =
        sharedPreferences.getBoolean(key, false)

    private fun saveBooleanDataByKey(key: String?, data: Boolean) {
        sharedPreferences.edit(commit = true) { putBoolean(key, data) }
    }

    private fun saveStringDataByKey(key: String?, data: String?) {
        sharedPreferences.edit(commit = true) { putString(key, data) }
    }

    private fun getStringDataByKey(key: String?): String =
        sharedPreferences.getString(key, "") ?: ""

    private fun saveIntDataByKey(key: String?, data: Int) {
        sharedPreferences.edit(commit = true) { putInt(key, data) }
    }

    private fun getIntDataByKey(key: String?): Int = sharedPreferences.getInt(key, 0)

}