package com.oc.catemoji.catoc.ui.onboarding.splash

import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.toIntro
import com.oc.catemoji.catoc.core.extention.toLanguage
import com.oc.catemoji.catoc.databinding.ActivitySplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : BaseActivity<ActivitySplashBinding, SplashViewModel>(
    ActivitySplashBinding::inflate,
    SplashViewModel::class.java
) {
    private var hasNavigated = false

    override fun initView() {
        ResourcesCompat.getFont(this, R.font.baloo2_extrabold)
        viewModel.startSplashTimer()
    }

    override fun viewListener() = Unit
    override fun bindViewModel() = Unit

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!hasNavigated) {
                    viewModel.navigateSignal.first { it }
                    navigateToNextScreen()
                }
            }
        }
    }

    private fun navigateToNextScreen() {
        if (hasNavigated || isFinishing || isDestroyed) return
        hasNavigated = true
        if (sharedPreferences.isLanuageScreen()) {
            toIntro()
        } else {
            toLanguage()
        }
    }

    override fun onScreenBackPressed(): Boolean = true
}
