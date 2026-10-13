package com.oc.catemoji.catoc.ui.main.successcosplay

import android.graphics.Rect
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.loadNativeCollabAds
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.policy
import com.oc.catemoji.catoc.core.extention.popBack
import com.oc.catemoji.catoc.core.extention.select
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.shareApp
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.toLangFromSetting
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.RateHelper
import com.oc.catemoji.catoc.databinding.FragmentSettingBinding
import com.oc.catemoji.catoc.databinding.FragmentSettingBinding.inflate
import com.oc.catemoji.catoc.databinding.FragmentSuccessCosplayBinding
import com.oc.catemoji.catoc.ui.main.cosplay.CosplayViewModel
import com.oc.catemoji.catoc.ui.main.setting.SettingViewModel
import com.oc.catemoji.catoc.ui.main.show.ShowViewModel
import com.oc.catemoji.catoc.utils.state.RateState

class SuccessCosplayFragment : BaseFragment<FragmentSuccessCosplayBinding, SuccessCosplayViewModel>( FragmentSuccessCosplayBinding::inflate, SuccessCosplayViewModel::class.java) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackPressHandler()
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {

                }
            }
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentSuccessCosplayBinding = FragmentSuccessCosplayBinding.inflate(inflater, container, false)

    override fun initView() {
        loadNativeCollabAds(
            getString(R.string.native_cl_cosplaySuccess), binding.flNativeCollab
        )
        binding.apply {
            txtShow.isSelected = true
            setupActionBar()
            val userBitmap = viewModelActivity.userResultBitmap
            if (userBitmap != null && !userBitmap.isRecycled) {
                imvImage2.setImageBitmap(userBitmap)
            }

            // imvImage3 = ảnh cosplay gốc


            val currentBinding = this
            view33.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                root.doOnPreDraw { alignProgressMarkers(currentBinding) }
            }
            updateProgressBar(viewModelActivity.cosplayPercent)
        }
    }
    private fun updateProgressBar(percent: Int) {
        val currentBinding = binding
        val safePercent = percent.coerceIn(0, 100)
        currentBinding.tvMatchPercent.text = "$safePercent/100"
        currentBinding.view33.updateLayoutParams<ConstraintLayout.LayoutParams> {
            horizontalBias = safePercent / 100f
        }
        currentBinding.root.doOnPreDraw { alignProgressMarkers(currentBinding) }
    }

    private fun alignProgressMarkers(currentBinding: FragmentSuccessCosplayBinding) {
        val anchorBounds = Rect()
        currentBinding.layoutProgress.offsetDescendantRectToMyCoords(
            currentBinding.view33,
            anchorBounds.apply {
                set(0, 0, currentBinding.view33.width, currentBinding.view33.height)
            }
        )
        val anchorCenterX = anchorBounds.exactCenterX()
        currentBinding.imgStar.x = anchorCenterX - currentBinding.imgStar.width / 2f
        currentBinding.imgStar.y = anchorBounds.exactCenterY() - currentBinding.imgStar.height / 2f
        currentBinding.tvMatchPercent.x = currentBinding.layoutProgress.x + anchorCenterX -
            currentBinding.tvMatchPercent.width / 2f
    }
    private fun FragmentSuccessCosplayBinding.setupActionBar() {
        actionBar.apply {
            tvCenter.select()
            setImageActionBar(
                btnActionBarRight,
                R.drawable.ic_home
            )
            setTextActionBar(
                tvCenter,
                getString(R.string.successfully)
            )
        }
    }







    override fun viewListener() {
        binding.apply {
            setupActionBarListeners()
            setupNavigationListeners()
        }
    }

    private fun FragmentSuccessCosplayBinding.setupActionBarListeners() {
        actionBar.btnActionBarRight.onClick {
            showInter {
                findNavController().navigate(R.id.action_successCosplay_to_home)

            }
        }
    }

    private fun FragmentSuccessCosplayBinding.setupNavigationListeners() {
        btnTryAgain.onClick {
            showInter {

            viewModelActivity.shouldRestartShow = true  // ← báo ShowFragment reset
            popBack()
            }

    }
    }


    override fun observeData() {}

    override fun bindViewModel() {}
}
