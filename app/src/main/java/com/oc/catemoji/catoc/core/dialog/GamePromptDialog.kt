package com.oc.catemoji.catoc.core.dialog

import android.app.Activity
import android.app.Dialog
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.DialogGamePromptBinding

enum class GamePrompt { EXIT, WATCH_ADS }

class GamePromptDialog(
    activity: Activity,
    prompt: GamePrompt,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) : Dialog(activity) {
    init {
        val binding = DialogGamePromptBinding.inflate(activity.layoutInflater)
        setContentView(binding.root)
        setCanceledOnTouchOutside(false)
        setOnCancelListener { onCancel() }
        val watchingAds = prompt == GamePrompt.WATCH_ADS
        binding.apply {
            txtPromptTitle.setText(if (watchingAds) R.string.bonus_treats_title else R.string.exit_screen_title)
            txtPromptDescription.setText(if (watchingAds) R.string.bonus_treats_description else R.string.exit_screen_description)
            btnPromptConfirm.setText(if (watchingAds) R.string.watch_ads else R.string.exit)
            imgPromptBone.isVisible = watchingAds
            btnPromptCancel.isVisible = !watchingAds
            btnPromptClose.isVisible = watchingAds
            btnPromptConfirm.onClick { onConfirm() }
            btnPromptCancel.onClick { onCancel() }
            btnPromptClose.onClick { onCancel() }
        }
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
