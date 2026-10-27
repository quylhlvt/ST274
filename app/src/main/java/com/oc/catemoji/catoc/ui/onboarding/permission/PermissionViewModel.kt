package com.oc.catemoji.catoc.ui.onboarding.permission

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
@HiltViewModel
class PermissionViewModel @Inject constructor() : ViewModel() {

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

}
