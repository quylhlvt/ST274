package com.oc.catemoji.catoc.ui.onboarding.permission

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.oc.catemoji.catoc.MyApplication
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.extention.checkPermissions
import com.oc.catemoji.catoc.core.extention.hideNavigation

/** Shared permission policy; Android dialogs remain owned by the visible Activity. */
class MicrophonePermissionRequest(
    private val activity: AppCompatActivity,
    private val onResult: (Boolean) -> Unit,
) {
    private val viewModel by lazy {
        (activity.application as MyApplication).sharedPermissionViewModel(activity)
    }
    private var settingsDialog: AlertDialog? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onMicrophonePermissionResult(granted)
        onResult(granted)
    }
    private val settings = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = isGranted()
        viewModel.onMicrophonePermissionResult(granted)
        onResult(granted)
    }

    fun isGranted(): Boolean = activity.checkPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))

    fun request() {
        when (viewModel.microphoneAction(
            isGranted(), activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
        )) {
            MicrophonePermissionAction.GRANTED -> {
                viewModel.onMicrophonePermissionResult(true)
                onResult(true)
            }
            MicrophonePermissionAction.REQUEST -> {
                viewModel.onMicrophoneRequested()
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
            MicrophonePermissionAction.SETTINGS -> showSettings()
        }
    }

    private fun showSettings() {
        if (settingsDialog?.isShowing == true) return
        settingsDialog = AlertDialog.Builder(activity)
            .setTitle(R.string.microphone)
            .setMessage(R.string.play_microphone_permission)
            .setPositiveButton(R.string.settings) { _, _ ->
                settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${activity.packageName}".toUri()))
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onResult(false) }
            .setOnCancelListener { onResult(false) }
            .create().also {
                it.show()
                it.hideNavigation(true)
            }
    }

    fun dispose() {
        if (settingsDialog?.isShowing == true) onResult(false)
        settingsDialog?.dismiss()
        settingsDialog = null
    }
}
