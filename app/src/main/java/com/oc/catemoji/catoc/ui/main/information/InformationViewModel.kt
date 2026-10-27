package com.oc.catemoji.catoc.ui.main.information

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.oc.catemoji.catoc.data.repository.BattleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ProfileSaveState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
class InformationViewModel @Inject constructor(
    private val battleRepository: BattleRepository,
    private val sharedPreferences: SharedPreferencesManager,
) : ViewModel() {
    private val _saveState = MutableStateFlow(ProfileSaveState())
    val saveState = _saveState.asStateFlow()

    fun saveProfile(name: String) {
        if (_saveState.value.saving || _saveState.value.saved) return
        _saveState.value = ProfileSaveState(saving = true)
        viewModelScope.launch {
            try {
                battleRepository.createProfile(name)
                withContext(Dispatchers.IO) {
                    sharedPreferences.setInformationScreen(true)
                }
                _saveState.value = ProfileSaveState(saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _saveState.value = ProfileSaveState(failed = true)
            }
        }
    }
}
