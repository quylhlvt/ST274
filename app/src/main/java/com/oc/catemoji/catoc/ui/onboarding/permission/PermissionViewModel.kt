package com.oc.catemoji.catoc.ui.onboarding.permission

import androidx.lifecycle.ViewModel
import com.oc.catemoji.catoc.core.helper.PermissionHelper
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
@HiltViewModel
class PermissionViewModel @Inject constructor() : ViewModel() {

    // ✅ Count tập trung, share across fragments
    private val _storageDenyCount = MutableStateFlow(0)
    private val _notificationDenyCount = MutableStateFlow(0)

    val storageDenyCount: StateFlow<Int> = _storageDenyCount.asStateFlow()
    val notificationDenyCount: StateFlow<Int> = _notificationDenyCount.asStateFlow()

    fun onStorageDenied()      { _storageDenyCount.value++ }
    fun onStorageGranted()     { _storageDenyCount.value = 0 }
    fun onNotificationDenied() { _notificationDenyCount.value++ }
    fun onNotificationGranted(){ _notificationDenyCount.value = 0 }

    fun shouldGoToSettings(isStorage: Boolean): Boolean {
        val count = if (isStorage) _storageDenyCount.value else _notificationDenyCount.value
        return count >= 2
    }

    fun getStoragePermissions()      = PermissionHelper.storagePermission
    fun getNotificationPermissions() = PermissionHelper.notificationPermission
}