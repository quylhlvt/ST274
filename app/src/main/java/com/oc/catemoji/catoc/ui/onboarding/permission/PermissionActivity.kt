package com.oc.catemoji.catoc.ui.onboarding.permission

import android.content.pm.PackageManager
import android.os.Build
import com.oc.catemoji.catoc.MyApplication
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.*
import com.oc.catemoji.catoc.core.helper.PermissionHelper
import com.oc.catemoji.catoc.databinding.ActivityPermissionBinding
import com.oc.catemoji.catoc.utils.key.RequestKey
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionActivity : BaseActivity<ActivityPermissionBinding, PermissionViewModel>(
    ActivityPermissionBinding::inflate,
    PermissionViewModel::class.java
) {
    private val permissionViewModel by lazy {
        (application as MyApplication).sharedPermissionViewModel(this)
    }
    private val microphonePermission = MicrophonePermissionRequest(this) { granted ->
        updateMicrophoneUI(granted)
    }

    override fun viewListener() {
        binding.swPermission.onClick(1500) { handlePermissionRequest(isStorage = true) }
        binding.swNotification.onClick(1500) { handlePermissionRequest(isStorage = false) }
        binding.swMicrophone.onClick(1500) { microphonePermission.request() }
        binding.tvContinue.onClick(1000) {
            handleContinue()
        }
    }

    override fun initView() {

        binding.setupActionBar()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            binding.btnStorage.visible()
            binding.btnNotification.gone()
        } else {
            binding.btnNotification.visible()
            binding.btnStorage.gone()
        }
        updatePermissionUI(requireContext().checkPermissions(PermissionHelper.storagePermission), true)
        updatePermissionUI(requireContext().checkPermissions(PermissionHelper.notificationPermission), false)
        updateMicrophoneUI(microphonePermission.isGranted())
    }

    private fun ActivityPermissionBinding.setupActionBar() {
        actionBar.apply {
            tvStart.select()
            setTextActionBar(tvStart, getString(R.string.permission))
        }
    }

    private fun handlePermissionRequest(isStorage: Boolean) {
        val perms = if (isStorage) PermissionHelper.storagePermission
        else PermissionHelper.notificationPermission

        when {
            requireContext().checkPermissions(perms) ->
                showToast(if (isStorage) R.string.granted_storage else R.string.granted_notification)
            permissionViewModel.shouldGoToSettings(isStorage) -> goToSettings()

            else -> requestPermission(
                perms,
                if (isStorage) RequestKey.STORAGE_PERMISSION_CODE
                else RequestKey.NOTIFICATION_PERMISSION_CODE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI(
            requireContext().checkPermissions(PermissionHelper.storagePermission),
            true
        )
        updatePermissionUI(
            requireContext().checkPermissions(PermissionHelper.notificationPermission),
            false
        )
        updateMicrophoneUI(microphonePermission.isGranted())
    }

    private fun updateMicrophoneUI(granted: Boolean) {
        binding.swMicrophone.setImageResource(if (granted) R.drawable.switch_on else R.drawable.switch_off)
    }

    override fun onScreenDestroyed() {
        microphonePermission.dispose()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        when (requestCode) {
            RequestKey.STORAGE_PERMISSION_CODE -> {
                if (granted) {
                    permissionViewModel.onStorageGranted()
                } else {
                    permissionViewModel.onStorageDenied()
                }
                updatePermissionUI(granted, true)
            }

            RequestKey.NOTIFICATION_PERMISSION_CODE -> {
                if (granted) {
                    permissionViewModel.onNotificationGranted()
                } else {
                    permissionViewModel.onNotificationDenied()
                }
                updatePermissionUI(granted, false)
            }
        }
    }

    private fun updatePermissionUI(granted: Boolean, isStorage: Boolean) {
        val imageView = if (isStorage) binding.swPermission else binding.swNotification
        imageView.setImageResource(if (granted) R.drawable.switch_on else R.drawable.switch_off)
    }


    override fun observeData() {}

    override fun initText() {
        binding.actionBar.tvCenter.select()

        val textRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            R.string.permission_access_notification_microphone
        } else {
            R.string.permission_access_photos_microphone
        }

        binding.txtPermission.text = listOf(
            getString(R.string.allow),
            getString(R.string.app_name),
            getString(textRes)
        ).joinToString(" ")
    }

    private fun handleContinue() {
        sharedPreferences.setPermissionScreen(true)
        if (sharedPreferences.isInformationScreen()) {
            toHomeFromPermission()
        } else {
            toInformation()
        }
    }

    override fun bindViewModel() {}

    override fun onScreenBackPressed(): Boolean {
        finishAffinity()
        return true
    }
}
