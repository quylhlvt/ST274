
package com.oc.catemoji.catoc.ui.onboarding.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
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
import com.oc.catemoji.catoc.core.extention.OuterStrokeShadownTextView
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
) , BackPressHandler {
    private val mainViewModel: ViewModelActivity by activityViewModels()
    var interCallBack: InterCallback? = null
    private var progressAnimator: ValueAnimator? = null
    private var currentOverlayFraction = 1f
    private var hasNavigated = false

    companion object {
        private const val MIN_SPLASH_MS  = 2_000L
        private const val API_TIMEOUT_MS = 8_000L
    }

    // ── INIT ──────────────────────────────────────────────────────────────────

    override fun initView() {
        Admob.getInstance().setTimeLimitShowAds(30000)
        Admob.getInstance().setTimeCountdownNativeCollab(15000)
        // ✅ Warm up font — giữ nguyên, nhẹ
        ResourcesCompat.getFont(requireContext(), R.font.baloo2_extrabold)

        // ✅ Bỏ OuterStrokeShadownTextView warm up — không cần thiết
        // val dummyView = OuterStrokeShadownTextView(requireContext())

        // ✅ Bỏ AsyncLayoutInflater ở đây — inflate fragment_home quá nặng
        // AsyncLayoutInflater(requireContext()).inflate(R.layout.fragment_home, null) { _, _, _ -> }

        checkAndClearDataIfNewVersion()

        binding.progressWrapper.post {
            startFakeProgress()

            // ✅ Preload fragment_home SAU khi splash đã hiển thị xong
            AsyncLayoutInflater(requireContext()).inflate(
                R.layout.fragment_home, null
            ) { _, _, _ -> }
        }
        interCallBack = object : InterCallback() {
            override fun onNextAction() {
                super.onNextAction()
                lifecycleScope.launch {
                    viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                        goToHome()
                    }
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
            // ✅ Xóa MMKV
            MMKV.defaultMMKV().clearAll()

            // ✅ Xóa SharedPreferences (giữ lại language)
            sharedPreferences.clearAll()

            // ✅ Xóa file cache
            context.filesDir.deleteRecursively()
            context.cacheDir.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()

            // ✅ Set lại version SAU khi clear
            sharedPreferences.setVersionCode(currentVersion)

            viewLifecycleOwner.lifecycleScope.launch {
                mainViewModel.forceReloadAll()
            }
        }
    }
    override fun viewListener() {}

    override fun observeData() {
        // Launch trên background, không block main thread
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.startSplashTimer(
                hasOnlineTemplates = mainViewModel.templates.value.any { it.id.startsWith("online_") },
                waitForOnline = {
                    mainViewModel.templates.first { list ->
                        list.any { it.id.startsWith("online_") }
                    }
                },
                waitForImages = {
                    mainViewModel.imagesReady.first { it }  // ✅ chờ true
                }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.readyToNavigate.collect {
                    completeProgress { goToHome() }
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

    // ── PROGRESS ──────────────────────────────────────────────────────────────

    // Giả lập 80% progress trong MIN_SPLASH_MS, dừng chờ data
    private fun startFakeProgress() {
        animateOverlayTo(targetFraction = 0.2f, duration = MIN_SPLASH_MS)
    }
    private fun animateOverlayTo(
        targetFraction: Float,
        duration: Long,
        onEnd: (() -> Unit)? = null
    ) {
        progressAnimator?.cancel()

        val overlay   = binding.progressOverlay
        val capRight  = binding.progressCap
        val container = binding.progressWrapper

        val containerWidth = container.width
        if (containerWidth <= 0) { onEnd?.invoke(); return }

        // Offset bù cho capRight margin (10dp start) + độ rộng cap (11dp)
        val density = resources.displayMetrics.density
        val capOffset = (10 + 11) * density  // marginStart + width của capRight

        progressAnimator = ValueAnimator.ofFloat(currentOverlayFraction, targetFraction).apply {
            this.duration = duration
            interpolator  = DecelerateInterpolator()

            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                currentOverlayFraction = fraction

                val density = resources.displayMetrics.density
                val total   = container.width.toFloat()

                // overlay.left = marginStart = 18dp (vị trí gốc của overlay trong container clip)
                // fraction=1 → overlay dịch trái về x=0 → translateX = -overlay.left
                // fraction=0 → overlay dịch phải ra ngoài → translateX = total - overlay.left
                val overlayLeft = overlay.left.toFloat() - dpToPx(requireContext(),18)  // lấy trực tiếp từ view, không cần tính dp

                val translateX = (total - overlayLeft) * (1f - fraction) - overlayLeft * fraction
                // rút gọn: translateX = (total - overlayLeft) * (1f - fraction) - overlayLeft * fraction
                //        = total*(1-f) - overlayLeft*(1-f) - overlayLeft*f
                //        = total*(1-f) - overlayLeft

                // Đơn giản hơn:
                val tx = total * (1f - fraction) - overlayLeft

                overlay.translationX = tx
                capRight.translationX = tx
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
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
    // ── NAVIGATE ──────────────────────────────────────────────────────────────

    private fun goToHome() {
        if (hasNavigated) return
        if (!isAdded || isDetached || isRemoving) return
        hasNavigated = true

        if (!isLanuageScreen()) { toLanguage(); return }
        toIntro()
    }

    // ── NETWORK ───────────────────────────────────────────────────────────────

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
    } catch (e: Exception) { false }

    // ── LIFECYCLE ─────────────────────────────────────────────────────────────
    override fun onPause() {
        super.onPause()
        // Dừng animator, giữ nguyên currentOverlayFraction
        progressAnimator?.pause()
    }

    override fun onResume() {
        super.onResume()
        // Chạy tiếp từ chỗ dừng
        progressAnimator?.resume()
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
