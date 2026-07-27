package com.oc.catemoji.catoc.core.extention

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.rewarded.RewardItem
import com.lvt.ads.callback.InterCallback
import com.lvt.ads.callback.RewardCallback
import com.lvt.ads.event.AdmobEvent
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R


fun Fragment.showInter(action: (() -> Unit)) {
    Admob.getInstance().showInterAll(requireActivity(), object : InterCallback() {
        override fun onNextAction() {
            super.onNextAction()
            action()
        }
    })
}
fun Fragment.showRewardAds1(
    onRewardSuccess: () -> Unit,
    onAdClosed: () -> Unit = {}
) {
    var earned = false
    Admob.getInstance().loadAndShowRewardAds(
        requireActivity(),
        getString(R.string.reward_random),
        object : RewardCallback() {
            override fun onEarnedReward(rewardItem: RewardItem?) {
                earned = true
            }
            override fun onAdClosed() {
                super.onAdClosed()
                if (earned) {
                    onRewardSuccess()
                } else {
                    Toast.makeText(requireActivity(), getString(R.string.please_watch_full_ads), Toast.LENGTH_SHORT).show()
                }
                onAdClosed()
            }
            override fun onAdFailedToLoad() {
                super.onAdFailedToLoad()
                Toast.makeText(requireActivity(), getString(R.string.load_ads_fail), Toast.LENGTH_SHORT).show()
                onAdClosed()
            }
            override fun onAdFailedToShow(codeError: Int) {
                super.onAdFailedToShow(codeError)
                Toast.makeText(requireActivity(), getString(R.string.load_ads_fail), Toast.LENGTH_SHORT).show()
                onAdClosed()
            }
        }
    )
}
fun Fragment.loadNativeCollabAds(id: String, layout: FrameLayout) {
    Admob.getInstance().loadNativeCollap(requireActivity(), id, layout)
}

fun Fragment.showInterAll() {
    Admob.getInstance().showInterAll(requireActivity(), object : InterCallback() {
        override fun onNextAction() {
            super.onNextAction()
        }
    })
}
fun Fragment.logEvent(nameEvent: String, value: String) {
    val bundle = Bundle()
    bundle.putString("link", value)
    AdmobEvent.logEvent(requireActivity(), nameEvent, bundle)
}
fun Fragment.logEvent(nameEvent: String) {
    AdmobEvent.logEvent(requireActivity(), nameEvent, null)
}