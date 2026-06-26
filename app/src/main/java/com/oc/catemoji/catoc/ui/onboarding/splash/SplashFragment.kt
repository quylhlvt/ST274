package com.oc.catemoji.catoc.ui.onboarding.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lvt.ads.callback.InterCallback
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.dpToPx
import com.oc.catemoji.catoc.core.extention.toIntro
import com.oc.catemoji.catoc.core.extention.toLanguage
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager.isLanuageScreen
import com.oc.catemoji.catoc.databinding.FragmentSplashBinding
import com.tencent.mmkv.MMKV
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class SplashFragment : BaseFragment<FragmentSplashBinding, SplashViewModel>(
    FragmentSplashBinding::inflate,
    SplashViewModel::class.java
), BackPressHandler {
    private val mainViewModel: ViewModelActivity by activityViewModels()
    var interCallBack: InterCallback? = null
    private var progressAnimator: ValueAnimator? = null
    private var currentOverlayFraction = 1f
    private var hasNavigated = false
    private var adReady = false

    companion object {
        private const val MIN_SPLASH_MS = 2_000L
        private const val API_TIMEOUT_MS = 8_000L
    }


    override fun initView() {
        ResourcesCompat.getFont(requireContext(), R.font.baloo2_extrabold)

        checkAndClearDataIfNewVersion()

        interCallBack = object : InterCallback() {
            override fun onNextAction() {
                super.onNextAction()
                adReady = true
                if (isAdded && !isDetached && !isRemoving) {
                    lifecycleScope.launch { completeProgress { goToHome() } }
                }
            }
        }

        Admob.getInstance().loadSplashInterAds(
            requireActivity(), getString(R.string.inter_splash), 30000, 3000, interCallBack
        )
    }

    private fun checkAndClearDataIfNewVersion() {
        val context = requireContext()
        val currentVersion = context.packageManager
            .getPackageInfo(context.packageName, 0).versionCode
        val savedVersion = sharedPreferences.getVersionCode()

        if (savedVersion != currentVersion) {
            MMKV.defaultMMKV().clearAll()
            sharedPreferences.clearAll()
            context.filesDir.deleteRecursively()
            context.cacheDir.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()

            sharedPreferences.setVersionCode(currentVersion)

            viewLifecycleOwner.lifecycleScope.launch {
                mainViewModel.forceReloadAll()
            }
        }
    }

    override fun viewListener() {}

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.readyToNavigate.collect {
                    if (!isNetworkAvailable()) {
                        completeProgress { goToHome() }
                    } else {
                        withTimeoutOrNull(30_000L) {
                            while (!adReady) delay(100)
                        }
                        if (!hasNavigated) {
                            completeProgress { goToHome() }
                        }
                    }
                }
            }
        }
    }

    override fun bindViewModel() {}

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentSplashBinding = FragmentSplashBinding.inflate(inflater, container, false)


    private fun startFakeProgress() {
        animateOverlayTo(targetFraction = 0.2f, duration = MIN_SPLASH_MS)
    }

    private fun animateOverlayTo(
        targetFraction: Float,
        duration: Long,
        onEnd: (() -> Unit)? = null
    ) {
        progressAnimator?.cancel()

        val overlay = binding.progressOverlay
        val capRight = binding.progressCap
        val container = binding.progressWrapper

        val containerWidth = container.width
        if (containerWidth <= 0) {
            onEnd?.invoke(); return
        }

        progressAnimator = ValueAnimator.ofFloat(currentOverlayFraction, targetFraction).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()

            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                currentOverlayFraction = fraction

                val total = container.width.toFloat()
                val overlayLeft = overlay.left.toFloat() - dpToPx(requireContext(), 18)
                val tx = total * (1f - fraction) - overlayLeft

                overlay.translationX = tx
                capRight.translationX = tx
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {}
            })

            start()
        }
    }

    private fun completeProgress(onDone: () -> Unit) {
        animateOverlayTo(targetFraction = 0f, duration = 500L, onEnd = {
            binding.progressWrapper.visibility = View.GONE
            onDone()
        })
    }


    private fun goToHome() {
        if (hasNavigated) return
        if (!isAdded || isDetached || isRemoving) return
        hasNavigated = true

        if (!isLanuageScreen()) {
            toLanguage(); return
        }
        toIntro()
    }


    private fun isNetworkAvailable(): Boolean = try {
        val cm = requireContext().getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    } catch (e: Exception) {
        false
    }

    override fun onPause() {
        super.onPause()
        progressAnimator?.pause()
    }

    override fun onResume() {
        super.onResume()
        progressAnimator?.resume()

        if (adReady && !hasNavigated) {
            adReady = false
            lifecycleScope.launch { completeProgress { goToHome() } }
            return
        }

        binding.progressWrapper.post {
            if (!isAdded || isDetached) return@post

            startFakeProgress()

            // ✅ Chờ frame đầu tiên thực sự được render ra màn hình
            binding.root.viewTreeObserver.addOnPreDrawListener(object :
                android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.root.viewTreeObserver.removeOnPreDrawListener(this)

                    // Frame đầu tiên đã sẵn sàng render → bắt đầu đếm giờ
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.startSplashTimer(
                            hasOnlineTemplates = mainViewModel.templates.value.any {
                                it.id.startsWith("online_")
                            },
                            waitForOnline = {
                                mainViewModel.templates.first { list ->
                                    list.any { it.id.startsWith("online_") }
                                }
                            },
                            waitForImages = {
                                mainViewModel.imagesReady.first { it }
                            }
                        )
                    }

                    AsyncLayoutInflater(requireContext()).inflate(
                        R.layout.fragment_home, null
                    ) { _, _, _ -> }

                    return true  // ✅ true = cho phép draw bình thường
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressAnimator?.cancel()
        progressAnimator = null
    }

    override fun onBackPressed(): Boolean {
        return true
    }
}