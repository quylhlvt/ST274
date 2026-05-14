package com.oc.catemoji.catoc.core.dialog

import android.app.Activity
import android.widget.Toast
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseDialog
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.DialogCreateNameBinding


class CreateNameDialog(val context: Activity) :
    BaseDialog<DialogCreateNameBinding>(context, maxWidth = true, maxHeight = true) {
    override val layoutId: Int = R.layout.dialog_create_name
    override val isCancelOnTouchOutside: Boolean = false
    override val isCancelableByBack: Boolean = false

    var onNoClick: (() -> Unit) = {}
    var onYesClick: ((String) -> Unit) = {}
    var onDismissClick: (() -> Unit) = {}

    override fun initView() {
    }

    override fun initAction() {
        binding.apply {
            tvNo.onClick {
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(edtName.windowToken, 0)
                edtName.clearFocus()
                onNoClick.invoke()
            }
            tvYes.onClick {
                val input = edtName.text.toString().trim()
                if (input.isEmpty()) {
                    Toast.makeText(context,
                        context.getString(R.string.please_enter_your_package_name),
                        Toast.LENGTH_SHORT).show()
                } else {
                    // ✅ Hide keyboard trước
                    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(edtName.windowToken, 0)
                    edtName.clearFocus()
                    onYesClick.invoke(input)
                }
            }
            flOutSide.setOnClickListener(null)

        }
    }

    override fun onDismissListener() {

    }
}