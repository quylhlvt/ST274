package com.oc.catemoji.catoc.core.dialog

import android.content.Context
import android.graphics.Color
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseDialog
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.DialogColorPickerBinding


class ChooseColorDialog(context: Context) : BaseDialog<DialogColorPickerBinding>(context,maxWidth = true, maxHeight = false) {
    override val layoutId: Int = R.layout.dialog_color_picker
    override val isCancelOnTouchOutside: Boolean =true
    override val isCancelableByBack: Boolean = false

    var onDoneEvent: ((Int) -> Unit) = {}
    var onCloseEvent: (() -> Unit) = {}
    var onDismissEvent: (() -> Unit) = {}
    private var color = Color.WHITE
    override fun initView() {

        binding.apply {
            colorPickerView.apply {
                hueSliderView = hueSlider
            }
        }
    }

    override fun initAction() {
        binding.apply {
            colorPickerView.setOnColorChangedListener { color = it }
            btnClose.onClick { onCloseEvent.invoke() }
            btnDone.onClick { onDoneEvent.invoke(color) }
        }
    }

    override fun onDismissListener() {
        onDismissEvent.invoke()
    }

}