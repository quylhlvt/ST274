package com.oc.catemoji.catoc.core.dialog

import android.app.Activity
import android.app.Dialog
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.databinding.DialogHowToPlayBinding

class HowToPlayDialog(activity: Activity, useMicrophone: Boolean) : Dialog(activity) {
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogHowToPlayBinding.inflate(activity.layoutInflater)
        setContentView(binding.root)
        setCanceledOnTouchOutside(false)
        binding.txtGuideInstructions.setText(
            if (useMicrophone) R.string.how_to_play_voice else R.string.how_to_play_tap
        )
        binding.btnCloseGuide.setOnClickListener { dismiss() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }
    }

    override fun show() {
        super.show()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.52f)
        }
        hideNavigation(true)
    }
}
