// RateHelper.kt (updated)
package com.oc.catemoji.catoc.core.helper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.oc.catemoji.catoc.core.dialog.RateDialog
import com.oc.catemoji.catoc.utils.state.RateState
import com.google.android.play.core.review.ReviewManagerFactory

object RateHelper {

    fun showRateDialog(
        activity: FragmentActivity,
        preference: SharedPreferencesManager,
        onRateResult: (RateState) -> Unit = {}
    ) {
        val dialogRate = RateDialog(activity)
        dialogRate.show(activity.supportFragmentManager, "RateDialog")

        dialogRate.onRateLess3 = {
            preference.setRateRequest(true) // Mark as requested (low rating = maybe feedback)
            onRateResult(RateState.LESS3)
        }

        dialogRate.onRateGreater3 = {
            preference.setRateRequest(true) // Mark that we asked for rate
            reviewApp(activity)
            onRateResult(RateState.GREATER3)
        }

        dialogRate.onCancel = {
            onRateResult(RateState.CANCEL)
        }
    }

    fun reviewApp(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                // No need to listen — flow completes silently
            } else {
                // Fallback: open Play Store directly
                val packageName = activity.packageName
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                } catch (e: Exception) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
            }
        }
    }
}