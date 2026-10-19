package com.oc.catemoji.catoc.core.extention

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import android.view.View
import androidx.core.content.getSystemService
import com.google.android.gms.ads.rewarded.RewardItem
import com.lvt.ads.callback.InterCallback
import com.lvt.ads.callback.RewardCallback
import com.lvt.ads.event.AdmobEvent
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R

fun Activity.showInter(action: () -> Unit) {
    Admob.getInstance().showInterAll(this, object : InterCallback() {
        override fun onNextAction() {
            super.onNextAction()
            if (!isFinishing && !isDestroyed) action()
        }
    })
}

fun Activity.loadNativeCollabAds(id: String, layout: FrameLayout) {
    Admob.getInstance().loadNativeCollap(this, id, layout)
}

fun Activity.showRewardAds1(onRewardSuccess: () -> Unit, onAdClosed: () -> Unit = {}) {
    var earned = false
    Admob.getInstance().loadAndShowRewardAds(this, getString(R.string.reward_random), object : RewardCallback() {
        override fun onEarnedReward(rewardItem: RewardItem?) { earned = true }
        override fun onAdClosed() {
            super.onAdClosed()
            if (earned) onRewardSuccess() else Toast.makeText(this@showRewardAds1, R.string.please_watch_full_ads, Toast.LENGTH_SHORT).show()
            onAdClosed()
        }
        override fun onAdFailedToLoad() { super.onAdFailedToLoad(); onAdClosed() }
        override fun onAdFailedToShow(codeError: Int) { super.onAdFailedToShow(codeError); onAdClosed() }
    })
}

fun Activity.logEvent(nameEvent: String, value: String) {
    val bundle = Bundle().apply { putString("link", value) }
    AdmobEvent.logEvent(this, nameEvent, bundle)
}

fun Activity.logEvent(nameEvent: String) = AdmobEvent.logEvent(this, nameEvent, null)

fun Activity.logEventSocial(nameEvent: String, nameEvent1: String, value: String) {
    val bundle = Bundle().apply { putString("link_$nameEvent", value) }
    AdmobEvent.logEvent(this, nameEvent1, bundle)
}
