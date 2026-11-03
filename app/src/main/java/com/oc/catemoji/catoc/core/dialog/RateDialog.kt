package com.oc.catemoji.catoc.core.dialog

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.core.extention.strings
import com.oc.catemoji.catoc.databinding.DialogRateBinding
import kotlin.text.toInt

class RateDialog(private val activity: Activity) :
    Dialog(activity, R.style.BaseDialog), DefaultLifecycleObserver {

    private var _binding: DialogRateBinding? = null
    private val binding get() = _binding!!

    var onRateGreater3: (() -> Unit)? = null
    var onRateLess3: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private var rating: Int = 0
    private var ratingToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super<Dialog>.onCreate(savedInstanceState)
        _binding = DialogRateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCancelable(false)
        initView()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        initAction()
    }

    private fun initView() {
        binding.apply {
            tv1.isSelected = true
            tv2.isSelected = true
            btnVote.isSelected = true

        }
    }

    private fun initAction() {
        binding.btnExit.setOnClickListener {
            dismiss()
            onCancel?.invoke()
        }

        binding.btnVote.setOnClickListener {
            if (rating == 0) {
                ratingToast?.cancel()
                val toast = Toast.makeText(
                    context,
                    context.getString(R.string.rate_us_0),
                    Toast.LENGTH_SHORT
                )
                ratingToast = toast
                toast.show()
                binding.root.postDelayed({
                    toast.cancel()
                    if (ratingToast === toast) ratingToast = null
                }, 700L)
                return@setOnClickListener
            }

            dismiss()
            if (rating <= 3) {
                onRateLess3?.invoke()
            } else {
                onRateGreater3?.invoke()
            }
        }
        binding.ll1.setOnRatingChangeListener() { _, r, _ ->
            rating = r.toInt()
            when (rating) {
                0 -> setView(R.string.zero_star_title, R.string.zero_star, R.drawable.ic_rate_rero)
                1 -> setView(R.string.one_star_title, R.string.one_star, R.drawable.ic_rate_one)
                2 -> setView(R.string.two_star_title, R.string.one_star, R.drawable.ic_rate_two)
                3 -> setView(R.string.two_star_title, R.string.one_star, R.drawable.ic_rate_three)
                4 -> setView(R.string.four_star_title, R.string.four_star, R.drawable.ic_rate_four)
                5 -> setView(R.string.five_star_title, R.string.four_star, R.drawable.ic_rate_five)
            }
        }
    }

    private fun setView(titleRes: Int, descRes: Int,imgRes:Int) {
        binding.tv1.text = context.strings(titleRes)
        binding.tv2.text = context.strings(descRes)
        binding.imvAvtRate.setImageResource(imgRes)
    }

    override fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(this)
        super.show()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun dismiss() {
        ratingToast?.cancel()
        ratingToast = null
        (activity as? LifecycleOwner)?.lifecycle?.removeObserver(this)
        super.dismiss()
    }

    override fun onDestroy(owner: LifecycleOwner) = dismiss()
}