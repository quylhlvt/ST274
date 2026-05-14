package com.oc.catemoji.catoc.core.extention

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.helper.LanguageHelper.setLocale
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.oc.catemoji.catoc.utils.key.RequestKey


fun Context.checkPermissions(listPermission: Array<String>): Boolean {
    return listPermission.all {
        ContextCompat.checkSelfPermission( this,it) == PackageManager.PERMISSION_GRANTED
    }
}

fun Activity.requestPermission(permissions: Array<String>, requestCode: Int) {
    ActivityCompat.requestPermissions(this, permissions, requestCode)
}
fun Fragment.requestPermission(permissions: Array<String>, requestCode: Int) {
    requestPermissions(permissions, requestCode)
}
fun Fragment.openImagePicker() {
    val intent = Intent(Intent.ACTION_PICK)
    intent.type = "image/*"
    startActivityForResult(intent, RequestKey.PICK_IMAGE_REQUEST_CODE)
}
fun Activity.goToSettings() {
    setLocale(this)
    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.go_to_setting_message)
        .setMessage(R.string.go_to_setting_message)
        .setPositiveButton(R.string.settings) { dialog, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${this@goToSettings.packageName}".toUri()
            }
            this.startActivity(intent)
            dialog.dismiss()
            hideNavigation(true)
        }
        .setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.dismiss()
            hideNavigation(true)
        }
        .setCancelable(false)
        .create()

    dialog.show()
    val positiveButton: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    val negativeButton: Button = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
    positiveButton.setTextColor(getColor(R.color.app_color))
    negativeButton.setTextColor(getColor(R.color.black))
}
