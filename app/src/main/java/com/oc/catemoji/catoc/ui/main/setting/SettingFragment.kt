package com.oc.catemoji.catoc.ui.main.setting

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.policy
import com.oc.catemoji.catoc.core.extention.popBack
import com.oc.catemoji.catoc.core.extention.select
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.shareApp
import com.oc.catemoji.catoc.core.extention.toHomeFromSetting
import com.oc.catemoji.catoc.core.extention.toLangFromSetting
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.RateHelper
import com.oc.catemoji.catoc.databinding.FragmentQuickBinding
import com.oc.catemoji.catoc.databinding.FragmentSettingBinding
import com.oc.catemoji.catoc.utils.state.RateState
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue
@AndroidEntryPoint
class SettingFragment : BaseFragment<FragmentSettingBinding, SettingViewModel>( FragmentSettingBinding::inflate, SettingViewModel::class.java) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackPressHandler()
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    popBack()
                }
            }
        )
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentSettingBinding = FragmentSettingBinding.inflate(inflater, container, false)

    override fun initView() {
        binding.apply {

            setupActionBar()
            setupActionTiltleBar()
            setupRateButton()
        }
    }

    private fun FragmentSettingBinding.setupActionBar() {
        actionBar.apply {
            setImageActionBar(
                btnActionBarLeft,
                R.drawable.back_app
            )
        }
    }
 private fun FragmentSettingBinding.setupActionTiltleBar() {
        layoutTitleBar.apply {
            layoutTitle.apply {
                txt1.isSelected = true
                txt2.isSelected = true
                txt3.isSelected = true
                txt4.isSelected = true
                setTextActionBar(
                    txtLang,
                    getString(R.string.settings)
                )
                txtLang.isSelected =true
            }
        }
    }



    private fun FragmentSettingBinding.setupRateButton() {
        if (sharedPreferences.isRateRequest()) {
            btnRate.gone()
        } else {
            btnRate.visible()
        }
    }



    override fun viewListener() {
        binding.apply {
            setupActionBarListeners()
            setupNavigationListeners()
        }
    }

    private fun FragmentSettingBinding.setupActionBarListeners() {
        actionBar.btnActionBarLeft.onClick {
            popBack()
        }
    }

    private fun FragmentSettingBinding.setupNavigationListeners() {
        btnLang.onClick {
            toLangFromSetting()
        }

        btnPolicy.onClick {
            policy()
        }

        btnRate.onClick {
            RateHelper.showRateDialog(requireActivity(), sharedPreferences) { state ->
                if (state != RateState.CANCEL) {
                    btnRate.gone()
                    showToast(R.string.have_rated)
                }
            }
        }

        btnShare.onClick {
            shareApp()
        }
    }


    override fun observeData() {}

    override fun bindViewModel() {}
}