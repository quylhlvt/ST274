package com.oc.catemoji.catoc.core.extention

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.lvt.ads.callback.InterCallback
import com.lvt.ads.event.AdmobEvent
import com.lvt.ads.util.Admob


fun Fragment.showInter(action: (() -> Unit)) {
    Admob.getInstance().showInterAll(requireActivity(), object : InterCallback() {
        override fun onNextAction() {
            super.onNextAction()
            action()
        }
    })
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