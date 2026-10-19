package com.oc.catemoji.catoc.core.extention

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.app.ShareCompat
import com.oc.catemoji.catoc.core.helper.RateHelper
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.oc.catemoji.catoc.utils.state.RateState

fun Activity.shareApp() {
    ShareCompat.IntentBuilder(this)
        .setType("text/plain")
        .setChooserTitle("Chooser title")
        .setText("http://play.google.com/store/apps/details?id=$packageName")
        .startChooser()
}

fun Activity.policy() {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/cat-avatar-cat-maker/home")))
}

fun Activity.rateApp(
    sharePreference: SharedPreferencesManager,
    onRateResult: (RateState) -> Unit = {}
) {
    RateHelper.showRateDialog(this, sharePreference, onRateResult)
}
