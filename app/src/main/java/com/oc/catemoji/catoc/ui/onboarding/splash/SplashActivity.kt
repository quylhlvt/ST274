package com.oc.catemoji.catoc.ui.onboarding.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.lvt.ads.callback.InterCallback
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.toIntro
import com.oc.catemoji.catoc.core.extention.toLanguage
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager.isLanuageScreen
import com.oc.catemoji.catoc.databinding.ActivitySplashBinding
import com.tencent.mmkv.MMKV
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class SplashActivity : BaseActivity<ActivitySplashBinding, SplashViewModel>(
    ActivitySplashBinding::inflate,
    SplashViewModel::class.java
) {
    private val mainViewModel: ViewModelActivity get() = viewModelActivity
    private var navigateJob: Job? = null
    private var interCallBack: InterCallback? = null
    private var progressAnimator: ValueAnimator? = null
    private var currentOverlayFraction = 1f
    private var hasNavigated = false

    override fun initView() {
        ResourcesCompat.getFont(this, R.font.baloo2_extrabold)
        resetAppStateForNewVersion()

        interCallBack = object : InterCallback() {
            override fun onNextAction() {
                super.onNextAction()
                viewModel.triggerNavigate()
            }
        }
        Admob.getInstance().loadSplashInterAds(
            this, getString(R.string.inter_splash), 30_000, 3_000, interCallBack
        )

        binding.progressWrapper.post { startFakeProgress() }
    }

    override fun viewListener() = Unit
    override fun bindViewModel() = Unit

    override fun observeData() {
        viewModel.startSplashTimer(
            hasNetwork = isNetworkAvailable(),
            hasOnlineTemplates = mainViewModel.templates.value.any { it.id.startsWith("online_") },
            templatesFlow = mainViewModel.templates,
            imagesReadyFlow = mainViewModel.imagesReady,
            localDataReadyFlow = mainViewModel.localDataReady
        )

        lifecycleScope.launch {
            viewModel.dataReadySignal.first { it }
            if (!isNetworkAvailable()) {
                viewModel.triggerNavigate()
            } else {
                withTimeoutOrNull(30_000L) {
                    viewModel.navigateSignal.first { it }
                } ?: viewModel.triggerNavigate()
            }
        }
    }

    private fun resetAppStateForNewVersion() {
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode
        if (SharedPreferencesManager.getVersionCode() == currentVersion) return

        MMKV.defaultMMKV()?.clearAll()
        SharedPreferencesManager.clearAll()
        cacheDir.deleteRecursively()
        externalCacheDir?.deleteRecursively()
        SharedPreferencesManager.setVersionCode(currentVersion)
        mainViewModel.forceReloadAll()
    }

    private fun startFakeProgress() {
        animateOverlayTo(targetFraction = 0.2f, duration = MIN_SPLASH_MS)
    }

    private fun animateOverlayTo(
        targetFraction: Float,
        duration: Long,
        onEnd: (() -> Unit)? = null
    ) {
        progressAnimator?.cancel()
        val container = binding.progressWrapper
        if (container.width <= 0) {
            onEnd?.invoke()
            return
        }

        progressAnimator = ValueAnimator.ofFloat(currentOverlayFraction, targetFraction).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                currentOverlayFraction = fraction
                val overlayLeft = binding.progressOverlay.left.toFloat() -
                    resources.getDimension(R.dimen.dimension_18)
                val translation = container.width * (1f - fraction) - overlayLeft
                binding.progressOverlay.translationX = translation
                binding.progressCap.translationX = translation
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd?.invoke() ?: Unit
            })
            start()
        }
    }

    private fun finishProgressAndNavigate() {
        if (hasNavigated || isFinishing || isDestroyed) return
        animateOverlayTo(targetFraction = 0f, duration = 500L) {
            binding.progressWrapper.visibility = View.GONE
            goToHome()
        }
    }

    private fun goToHome() {
        if (hasNavigated || isFinishing || isDestroyed) return
        hasNavigated = true
        if (!isLanuageScreen()) toLanguage() else toIntro()
    }

    private fun isNetworkAvailable(): Boolean = try {
        val manager = getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            manager.activeNetworkInfo?.isConnected == true
        }
    } catch (_: Exception) {
        false
    }

    override fun onPause() {
        navigateJob?.cancel()
        navigateJob = null
        progressAnimator?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        progressAnimator?.resume()
        if (hasNavigated) return
        if (viewModel.navigateSignal.value) {
            finishProgressAndNavigate()
            return
        }
        navigateJob?.cancel()
        navigateJob = lifecycleScope.launch {
            viewModel.navigateSignal.first { it }
            finishProgressAndNavigate()
        }
    }

    override fun onScreenDestroyed() {
        navigateJob?.cancel()
        interCallBack = null
        progressAnimator?.cancel()
        progressAnimator = null
    }

    override fun onScreenBackPressed(): Boolean = true

    private companion object {
        const val MIN_SPLASH_MS = 5_000L
    }
}
