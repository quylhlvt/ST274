package com.oc.catemoji.catoc.core.dialog

import android.app.Activity
import android.app.Dialog
import android.view.ViewGroup
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.DialogPlayModeBinding

class PlayModeDialog(
    activity: Activity,
    onTap: () -> Unit,
    onVoice: () -> Unit,
    onClose: () -> Unit,
) : Dialog(activity) {
    init {
        val binding = DialogPlayModeBinding.inflate(activity.layoutInflater)
        setContentView(binding.root)
        setCanceledOnTouchOutside(false)
        setOnCancelListener { onClose() }
        binding.btnTapMode.onClick { onTap() }
        binding.btnVoiceMode.onClick { onVoice() }
        binding.btnClose.onClick { onClose() }
    }

    override fun show() {
        super.show()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setDimAmount(0.4f)
        }
        hideNavigation(true)
    }
}
