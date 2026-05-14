package com.oc.catemoji.catoc.core.dialog

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent

import kotlin.apply
import kotlin.text.trim
import kotlin.toString
import androidx.core.view.isVisible
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseDialog
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.helper.BitmapHelper
import com.oc.catemoji.catoc.databinding.DialogSpeechBinding

class DialogSpeech(val mcontext: Context, val path: String) : BaseDialog<DialogSpeechBinding>(mcontext, maxWidth = true, maxHeight = true) {
    override val layoutId: Int = R.layout.dialog_speech
    override val isCancelOnTouchOutside: Boolean = false
    override val isCancelableByBack: Boolean = false
    var onDoneClick: ((Bitmap?) -> Unit) = { }

    override fun initView() {
        binding.apply {
            edtSpeech.isFocusableInTouchMode = true
            edtSpeech.isFocusable = true
            edtSpeech.postDelayed({
                edtSpeech.requestFocus()
                // ✅ Thêm dòng này
                val imm = mcontext.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(edtSpeech, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
            }, 100) // ✅ Tăng delay từ 30 lên 100 để dialog attach xong
            loadImage(mcontext, path, imvBubble)
        }
    }

    override fun initAction() {
        binding.apply {
            edtSpeech.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                    handleDone()
                    true
                } else {
                    false
                }
            }

            layoutRoot.onClick { handleDone() }

            edtSpeech.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    binding.tvGetText.text = p0.toString()
                }

                override fun afterTextChanged(p0: Editable?) {}
            })
        }
    }

    fun handleDone(){
        binding.apply {
            edtSpeech.clearFocus()
            val imm = mcontext.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(edtSpeech.windowToken, 0)

            edtSpeech.invisible()
            tvGetText.isVisible = !TextUtils.isEmpty(edtSpeech.text.toString().trim())
            val bitmap = BitmapHelper.getBitmapFromEditText(layoutBubble)
            onDoneClick.invoke(bitmap)
        }
    }

    override fun onDismissListener() {}
}