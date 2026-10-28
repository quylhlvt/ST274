package com.oc.catemoji.catoc.ui.onboarding.intro

import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.toHome
import com.oc.catemoji.catoc.core.extention.toInformation
import com.oc.catemoji.catoc.core.extention.toPermission
import com.oc.catemoji.catoc.databinding.ActivityIntroBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IntroActivity : BaseActivity<ActivityIntroBinding, IntroViewModel>(
    ActivityIntroBinding::inflate,
    IntroViewModel::class.java
) {
    @Inject
    lateinit var introAdapter: IntroAdapter

    override fun viewListener() {
        binding.btnNextPager.tvButton.onClick(200) {
            android.util.Log.d("PERF", "1. Button clicked: ${System.currentTimeMillis()}")
            viewModel.nextPage(binding.viewPager2.currentItem, introAdapter.itemCount)
        }

    }

    override fun initView() {

        binding.viewPager2.adapter = introAdapter
        binding.dotsIndicator.attachTo(binding.viewPager2)
        setOnChangeViewPager2()
    }

    override fun bindViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                introAdapter.submitList(state.pagesSplash)
                binding.apply {
                    viewPager2.currentItem = state.page
                    btnNextPager.tvButton.text = getString(state.textButtonRes)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.singleEvent.collect { event ->
                when (event) {
                    is IntroSingleEvent.NavigateToNextScreen -> when {
                        !sharedPreferences.isPermissionScreen() -> toPermission()
                        !sharedPreferences.isInformationScreen() -> toInformation()
                        else -> toHome()
                    }
                }
            }
        }
    }


    private fun setOnChangeViewPager2() {
        binding.viewPager2.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                viewModel.getPage(binding.viewPager2.currentItem)
            }
        })
    }

    override fun onScreenBackPressed(): Boolean {
        finishAffinity()
        return  true
    }
}
