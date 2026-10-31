package com.oc.catemoji.catoc.ui.main.play

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.oc.catemoji.catoc.R

/** A reward is delivered exclusively by onUserEarnedReward, never by dismiss/failure. */
internal class PlayRewardedAd(
    private val activity: Activity,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onEarned: () -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private var disposed = false
    var isBusy = false
        private set
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    private var ad: RewardedAd? = null

    fun show() {
        if (disposed || isBusy) return
        val request = ++generation
        isBusy = true
        onBusyChanged(true)
        handler.postDelayed({
            if (!disposed && generation == request && ad == null && isBusy) {
                generation++
                finish(unavailable = true)
            }
        }, 30_000)
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                if (disposed || request != generation) return@runOnUiThread
                RewardedAd.load(activity.applicationContext, activity.getString(R.string.reward_random),
                    AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            if (!disposed && request == generation) finish(unavailable = true)
                        }
                        override fun onAdLoaded(loaded: RewardedAd) {
                            if (disposed || request != generation || activity.isFinishing || activity.isDestroyed) return
                            ad = loaded
                            loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() = finish()
                                override fun onAdFailedToShowFullScreenContent(error: AdError) = finish(unavailable = true)
                            }
                            loaded.show(activity) { onEarned() }
                        }
                    })
            }
        }
    }

    private fun finish(unavailable: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        ad = null
        isBusy = false
        if (!disposed) {
            onBusyChanged(false)
            if (unavailable) onUnavailable()
        }
    }

    fun dispose() {
        disposed = true
        generation++
        handler.removeCallbacksAndMessages(null)
        ad?.fullScreenContentCallback = null
        ad = null
    }
}
