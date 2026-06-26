package com.oc.catemoji.catoc.ui.onboarding.intro

import android.content.Intent
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.toHome
import com.oc.catemoji.catoc.core.extention.toIntro
import com.oc.catemoji.catoc.core.extention.toPermission
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager.isPermissionScreen
import com.oc.catemoji.catoc.databinding.FragmentIntroBinding
import com.oc.catemoji.catoc.databinding.FragmentPermissionBinding
import com.oc.catemoji.catoc.ui.onboarding.permission.PermissionViewModel
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.text.compareTo

@AndroidEntryPoint
class IntroFragment : BaseFragment<FragmentIntroBinding, IntroViewModel>(
    FragmentIntroBinding::inflate,
    IntroViewModel::class.java
), BackPressHandler  {
    @Inject
    lateinit var introAdapter: IntroAdapter

    override fun viewListener() {
        binding.btnNextPager.tvButton.onClick(200) {
            android.util.Log.d("PERF", "1. Button clicked: ${System.currentTimeMillis()}")
            viewModel.nextPage(binding.viewPager2.currentItem, introAdapter.itemCount)
        }
//         binding.btnNextPager.tvButton.onClick(200) {
//            android.util.Log.d("PERF", "1. Button clicked: ${System.currentTimeMillis()}")
//            viewModel.nextPage(binding.viewPager2.currentItem, introAdapter.itemCount)
//        }

        binding.apply {
           viewPager2.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    if (position == 1) {
                        nativeAds.gone()
                    } else {
                        nativeAds.visible()
                    }
                }
            })
        }

    }
    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentIntroBinding = FragmentIntroBinding.inflate(inflater, container, false)

    override fun initView() {
        binding.viewPager2.adapter = introAdapter
//        binding.viewPager2.isUserInputEnabled = false
        Admob.getInstance().loadNativeAd(
            requireContext(),
            getString(R.string.native_intro),
            binding.nativeAds,
            R.layout.ads_native_medium_btn_bottom
        )
        binding.viewPager2.adapter = introAdapter
        binding.dotsIndicator.attachTo(binding.viewPager2)
        setOnChangeViewPager2()

//        binding.textView.text = "Home Fragment"
//        binding.btnTest.setOnClickListener {
//            showSnackbar("Xin chào từ Home!")
//        }
    }

    override fun observeData() {
//        viewModel.data.observe(viewLifecycleOwner) { text ->
//            binding.textView.text = text
//        }
    }

    override fun bindViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                introAdapter.submitList(state.pagesSplash)
                binding.apply {
                    viewPager2.currentItem = state.page
                    btnNextPager.tvButton.text = getString(state.textButtonRes)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.singleEvent.collect { event ->
                when (event) {
                    is IntroSingleEvent.NavigateToNextScreen ->
                        if (sharedPreferences.isPermissionScreen())
                            toHome()
                        else
                            toPermission()
                }
            }
        }
    }


    private fun setOnChangeViewPager2() {
        binding.viewPager2.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                viewModel.getPage(binding.viewPager2.currentItem, introAdapter.itemCount)
            }
        })
    }

    override fun onBackPressed(): Boolean {
        requireActivity().finish()
        System.exit(0)
        return  true
    }
}