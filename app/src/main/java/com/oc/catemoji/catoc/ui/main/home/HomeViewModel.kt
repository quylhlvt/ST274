package com.oc.catemoji.catoc.ui.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private var savingReward = false
    private val _boneRewardBusy = MutableStateFlow(false)
    val boneRewardBusy = _boneRewardBusy.asStateFlow()
    private val _boneRewardSaveFailed = MutableStateFlow(false)
    val boneRewardSaveFailed = _boneRewardSaveFailed.asStateFlow()

    /** This version grants the reward directly when Watch Ads is confirmed. */
    fun claimBoneReward(addBones: suspend (Int) -> Unit) {
        if (savingReward) return
        savingReward = true
        _boneRewardBusy.value = true
        _boneRewardSaveFailed.value = false
        viewModelScope.launch {
            try {
                addBones(100)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _boneRewardSaveFailed.value = true
            } finally {
                savingReward = false
                _boneRewardBusy.value = false
            }
        }
    }

    fun consumeBoneRewardError() { _boneRewardSaveFailed.value = false }

    val modeDestination: HomePlayDestination?
        get() = savedState.get<String>(DESTINATION)?.let(HomePlayDestination::valueOf)

    fun showModeSelection(destination: HomePlayDestination) {
        savedState[DESTINATION] = destination.name
        savedState[VOICE_PENDING] = false
    }

    fun closeModeSelection() {
        savedState[DESTINATION] = null
        savedState[VOICE_PENDING] = false
    }

    fun requestVoiceMode(): Boolean {
        if (modeDestination == null || savedState.get<Boolean>(VOICE_PENDING) == true) return false
        savedState[VOICE_PENDING] = true
        return true
    }

    fun onMicrophonePermissionResult(granted: Boolean): HomePlayDestination? {
        if (savedState.get<Boolean>(VOICE_PENDING) != true) return null
        savedState[VOICE_PENDING] = false
        return if (granted) selectMode() else null
    }

    fun selectMode(): HomePlayDestination? = modeDestination.also { closeModeSelection() }

    private companion object {
        const val DESTINATION = "home_mode_destination"
        const val VOICE_PENDING = "home_voice_permission_pending"
    }
}

enum class HomePlayDestination { BATTLE, TRAINING }
