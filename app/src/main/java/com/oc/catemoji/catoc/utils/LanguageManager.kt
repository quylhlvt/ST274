package com.oc.catemoji.catoc.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LanguageManager {
    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage

    fun updateLanguage(languageCode: String) {
        _currentLanguage.value = languageCode
    }
}