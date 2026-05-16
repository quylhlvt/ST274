package com.oc.catemoji.catoc.ui.main.successcosplay

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
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
            val cosplayBitmap = viewModelActivity.cosplayBitmap
            if (cosplayBitmap != null && !cosplayBitmap.isRecycled) {
              imvImage3.setImageBitmap(cosplayBitmap)
            }

            val percent = viewModelActivity.cosplayPercent
            binding.tvMatchPercent.text = "$percent%"
            updateProgressBar(percent)
        }
    }
    private fun updateProgressBar(percent: Int) {
        binding.progressTrack.post {
            val trackW = binding.progressTrack.width.toFloat()
            val fillMarginStartPx = 7 * resources.displayMetrics.density // margin 7dp từ XML
            val fillW = trackW - fillMarginStartPx
            val targetScale = percent / 100f
            val adjustedScale = targetScale * fillW / trackW

            binding.progressFill.pivotX = 0f
            binding.progressFill.pivotY = binding.progressFill.height / 2f
            binding.progressFill.scaleX = adjustedScale
            binding.progressFill.scaleY = 1f

            val starW = binding.imgStar.width.toFloat()
            binding.imgStar.translationX = fillMarginStartPx + fillW * targetScale - starW / 2f
        }
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
                getString(R.string.successful)
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

            val cosplayEntry = runCatching {
                findNavController().getBackStackEntry(R.id.cosplay)
            }.getOrNull()

            cosplayEntry?.let {
                val factory = androidx.hilt.navigation.HiltViewModelFactory(requireContext(), it)
                val cosplayViewModel = androidx.lifecycle.ViewModelProvider(it, factory)[CosplayViewModel::class.java]
                cosplayViewModel.randomize()
            }

            viewModelActivity.shouldRestartShow = true  // ← báo ShowFragment reset
            popBack()
            }

    }
    }


    override fun observeData() {}

    override fun bindViewModel() {}
}