package com.oc.catemoji.catoc.ui.main.home

import android.Manifest
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.OuterStrokeShadownTextView
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.toSettingFromHome
import com.oc.catemoji.catoc.core.helper.RateHelper
import com.oc.catemoji.catoc.core.helper.RateHelper.showRateDialog
import com.oc.catemoji.catoc.databinding.ActivityHomeBinding
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import com.oc.catemoji.catoc.utils.state.RateState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
@AndroidEntryPoint
class HomeActivity : BaseActivity<ActivityHomeBinding, HomeViewModel>(
    ActivityHomeBinding::inflate,
    HomeViewModel::class.java
) {

    private val mainViewModel: ViewModelActivity get() = viewModelActivity
    private var countRate = 0
private fun initNativeCollab() {
        Log.d("NativeCollab", "🔄 initNativeCollab called, stack: ${Thread.currentThread().stackTrace[3]}")
        Admob.getInstance().loadNativeCollapNotBanner(requireContext(), getString(R.string.native_cl_home), binding.flNativeCollab)
    }
    override fun initView() {
        Admob.getInstance().loadNativeAll(requireContext(), getString(R.string.native_all))
        Log.d("NativeCollab", "▶️ initView")
        Admob.getInstance().loadInterAll(requireContext(), getString(R.string.inter_all))
        initNativeCollab()
        binding.rcvMain.adapter = HomeMenuAdapter(::onHomeMenuClick)
        binding.actionBar.apply {
            setImageActionBar(btnActionBarRight, R.drawable.ic_settings)
        }
    }
    override fun onScreenStart() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        initNativeCollab()
    }

    override fun onScreenStop() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PERF2", "HomeActivity onViewCreated: ${System.currentTimeMillis()}")
    }

    override fun onResume() {
        super.onResume()
        Log.d("PERF2", "HomeActivity onResume: ${System.currentTimeMillis()}")
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarRight.onClick { toSettingFromHome() }
    }

    private fun onHomeMenuClick(action: HomeMenuAction) {
        when (action) {
            HomeMenuAction.DIY -> return
            HomeMenuAction.CREATE -> {
                val hasNetwork = isInternetAvailable(requireContext()) && isNetworkConnected(requireContext())
                when {
                    !hasNetwork -> showUnstableNetworkDialog()
                    viewModelActivity.templates.value.isEmpty() -> showLoadingDataDialog()
                    else -> startActivity(android.content.Intent(
                        requireContext(),
                        com.oc.catemoji.catoc.ui.main.createPony.ChooseCatActivity::class.java
                    ))
                }
            }

            HomeMenuAction.Pause -> {
                val hasNetwork = isInternetAvailable(requireContext()) && isNetworkConnected(requireContext())
                when {
                    !hasNetwork -> showUnstableNetworkDialog()
                    viewModelActivity.templates.value.isEmpty() -> showLoadingDataDialog()
                    else -> startActivity(android.content.Intent(
                        requireContext(),
                        com.oc.catemoji.catoc.ui.main.createPony.ChooseCatActivity::class.java
                    ).putExtra(
                        com.oc.catemoji.catoc.ui.main.createPony.ChooseCatActivity.EXTRA_PAUSE_CHALLENGE,
                        true
                    ))
                }
            }

            HomeMenuAction.MY_ALBUM -> {
                showInter {
                    activityRouter().navigate(R.id.action_home_to_myPony)
                }
            }

            HomeMenuAction.RANDOM -> {
                navigateWithCheck(R.id.action_home_to_random)
            }

            HomeMenuAction.COSPLAY -> {
                navigateWithCheck(R.id.action_home_to_cosplay)
            }

            HomeMenuAction.STUDIO -> {
                navigateStudioWithNetworkCheck()
            }
        }
    }

    private fun navigateStudioWithNetworkCheck() {
        val hasNetwork =
            isInternetAvailable(requireContext()) && isNetworkConnected(requireContext())

        if (!hasNetwork) {
            showUnstableNetworkDialog()
            return
        }

        activityRouter().navigate(R.id.action_home_to_studio)
    }

    private fun navigateWithCheck(destination: Int) {
        val hasNetwork = isInternetAvailable(requireContext()) && isNetworkConnected(requireContext())
        val hasData = viewModelActivity.templates.value.isNotEmpty() // ← bỏ dấu !

        when {
            !hasNetwork -> showUnstableNetworkDialog()
            !hasData -> showLoadingDataDialog()
            else -> activityRouter().navigate(destination)
        }
    }
    override fun observeData() {
        binding.root.post {
            Log.d("PERF2", "HomeActivity first frame: ${System.currentTimeMillis()}")
            if (!isAdded || isDetached) return@post


            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    mainViewModel.error.collect { error ->
                        error?.let { Log.e("HomeActivity", "❌ $it") }
                    }
                }
            }
        }
    }

    override fun bindViewModel() {}

    override fun onScreenBackPressed(): Boolean {
        countRate = sharedPreferences.isBackRequest() + 1
        sharedPreferences.setBackRequest(countRate)
        if (!sharedPreferences.isRateRequest() && countRate % 2 == 0) {
            showRateDialog(requireActivity(), sharedPreferences) { state ->
                if (state != RateState.CANCEL) showToast(R.string.have_rated)
                finishAffinity()
            }
        } else {
            finishAffinity()
        }
        return true
    }
}
