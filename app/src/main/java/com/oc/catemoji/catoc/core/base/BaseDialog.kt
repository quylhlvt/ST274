package com.oc.catemoji.catoc.core.base

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.databinding.DataBindingUtil
import androidx.viewbinding.ViewBinding
import androidx.core.graphics.drawable.toDrawable
import com.oc.catemoji.catoc.core.helper.LanguageHelper
import kotlin.apply

abstract class BaseDialog<VB : ViewBinding>(
    context: Context,
    private val gravity: Int = Gravity.CENTER,
    private val maxWidth: Boolean = false,
    private val maxHeight: Boolean = false
) : Dialog(context) {

    protected lateinit var binding: VB
    abstract val layoutId: Int
    abstract val isCancelOnTouchOutside: Boolean
    abstract val isCancelableByBack: Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        binding = DataBindingUtil.inflate(LayoutInflater.from(context), layoutId, null, false)
        setContentView(binding.root)

        setCancelable(isCancelableByBack)
        setCanceledOnTouchOutside(isCancelOnTouchOutside)

        setupWindow()
        initView()
        initAction()
    }

    private fun setupWindow() {
        window?.apply {
            setGravity(gravity)

            val width = if (maxWidth) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT
            val height = if (maxHeight) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT
            setLayout(width, height)

            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun dismiss() {
        super.dismiss()
        onDismissListener()
    }

    abstract fun initView()
    abstract fun initAction()
    abstract fun onDismissListener()
}
