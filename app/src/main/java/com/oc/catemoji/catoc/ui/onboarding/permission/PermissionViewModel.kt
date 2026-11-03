package com.oc.catemoji.catoc.ui.onboarding.permission

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val preferences: SharedPreferences,
) : ViewModel() {

    private var storageDenyCount = 0
    private var notificationDenyCount = 0

    fun onStorageDenied()      { storageDenyCount++ }
    fun onStorageGranted()     { storageDenyCount = 0 }
    fun onNotificationDenied() { notificationDenyCount++ }
    fun onNotificationGranted(){ notificationDenyCount = 0 }

    fun shouldGoToSettings(isStorage: Boolean): Boolean {
        val count = if (isStorage) storageDenyCount else notificationDenyCount
        return count >= 2
    }

    fun microphoneAction(granted: Boolean, shouldShowRationale: Boolean): MicrophonePermissionAction = when {
        granted -> MicrophonePermissionAction.GRANTED
        preferences.getBoolean(MICROPHONE_REQUESTED, false) && !shouldShowRationale ->
            MicrophonePermissionAction.SETTINGS
        else -> MicrophonePermissionAction.REQUEST
    }

    fun onMicrophoneRequested() {
        preferences.edit().putBoolean(MICROPHONE_REQUESTED, true).apply()
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        // A later revocation should allow Android to show the permission prompt again.
        if (granted) preferences.edit().putBoolean(MICROPHONE_REQUESTED, false).apply()
    }

    private companion object {
        const val MICROPHONE_REQUESTED = "microphone_permission_requested"
    }
}

enum class MicrophonePermissionAction { GRANTED, REQUEST, SETTINGS }
