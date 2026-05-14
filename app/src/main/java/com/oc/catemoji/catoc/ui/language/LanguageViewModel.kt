package com.oc.catemoji.catoc.ui.language

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.model.language.LanguageModel
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor( sharedPreferences: SharedPreferences) : ViewModel() {
    private val _languageList = MutableStateFlow<List<LanguageModel>>(emptyList())
    val languageList: StateFlow<List<LanguageModel>> = _languageList.asStateFlow()

    private val _codeLang = MutableStateFlow("")
    val codeLang: StateFlow<String> = _codeLang.asStateFlow()

    private val _isFirstLanguage = MutableStateFlow(false)
    val isFirstLanguage: StateFlow<Boolean> = _isFirstLanguage.asStateFlow()

    fun loadLanguages(currentLang: String) {
        viewModelScope.launch {
            val list = DataLocal.getLanguageList().toMutableList()
            Log.d("LANG", "List size = ${list.size}, data = $list")

            val index = list.indexOfFirst { it.code == currentLang }
            if (index != -1) {
                val selected = list.removeAt(index)
                list.add(0, selected.apply { if (!isFirstLanguage.value) activate = true })
            }
            _codeLang.value = currentLang
            _languageList.value = list
        }
    }

    fun selectLanguage(code: String) {
        _codeLang.value = code
        val updatedList = _languageList.value.map {
            it.copy(activate = it.code == code)
        }
        _languageList.value = updatedList
    }

    fun setFirstLanguage(isFirst: Boolean){
        _isFirstLanguage.value = isFirst
    }
}