package com.oc.catemoji.catoc.ui.main.home

import android.Manifest
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.fragment.app.viewModels
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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.base.BaseFragment
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
import com.oc.catemoji.catoc.databinding.FragmentHomeBinding
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import com.oc.catemoji.catoc.utils.state.RateState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.lang.System.exit
@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding, HomeViewModel>(
    FragmentHomeBinding::inflate, HomeViewModel::class.java
), BackPressHandler {

    private val mainViewModel: ViewModelActivity by activityViewModels()
    private var countRate = 0

    override fun inflateBinding(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): FragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false)

    private fun initNativeCollab() {
        Log.d("NativeCollab", "🔄 initNativeCollab called, stack: ${Thread.currentThread().stackTrace[3]}")
        Admob.getInstance().loadNativeCollapNotBanner(requireContext(), getString(R.string.native_cl_home), binding.flNativeCollab)
    }
    override fun initView() {
        Admob.getInstance().loadNativeAll(requireContext(), getString(R.string.native_all))
        Log.d("NativeCollab", "▶️ initView")
        Admob.getInstance().loadInterAll(requireContext(), getString(R.string.inter_all))
        initNativeCollab()
        binding.actionBar.apply {
            setImageActionBar(btnActionBarRight, R.drawable.ic_settings)
        }
    }
    override fun onFragmentStart() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        initNativeCollab()
    }

    override fun onFragmentStop() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PERF2", "HomeFragment onViewCreated: ${System.currentTimeMillis()}")
    }

    override fun onResume() {
        super.onResume()
        Log.d("PERF2", "HomeFragment onResume: ${System.currentTimeMillis()}")
    }

    override fun viewListener() {
        binding.apply {
            btnCreate.onClick {
                navigateWithCheck(R.id.action_home_to_createPony)
            }
            btnMyAlbum.onClick {
                showInter {
                    findNavController().navigate(R.id.action_home_to_myPony)
                }

            }
            btnRandom.onClick(1000) {
                navigateWithCheck(R.id.action_home_to_random)
            }
            btnCosPlay.onClick(1000) {
                navigateWithCheck(R.id.action_home_to_cosplay)
            }
            actionBar.btnActionBarRight.onClick { toSettingFromHome() }
        }
    }
    private fun navigateWithCheck(destination: Int) {
        val hasNetwork = isInternetAvailable(requireContext()) && isNetworkConnected(requireContext())
        val hasData = viewModelActivity.templates.value.isNotEmpty() // ← bỏ dấu !

        when {
            !hasNetwork -> showUnstableNetworkDialog()
            !hasData -> showLoadingDataDialog()
            else -> findNavController().navigate(destination)
        }
    }
    override fun observeData() {
        binding.root.post {
            Log.d("PERF2", "HomeFragment first frame: ${System.currentTimeMillis()}")
            if (!isAdded || isDetached) return@post
            binding.tv1.isSelected = true
            binding.tv2.isSelected = true
            binding.tv3.isSelected = true
            binding.tv4.isSelected = true

            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    mainViewModel.error.collect { error ->
                        error?.let { Log.e("HomeFragment", "❌ $it") }
                    }
                }
            }
        }
    }

    override fun bindViewModel() {}

    override fun onBackPressed(): Boolean {
        countRate = sharedPreferences.isBackRequest() + 1
        sharedPreferences.setBackRequest(countRate)
        if (!sharedPreferences.isRateRequest() && countRate % 2 == 0) {
            showRateDialog(requireActivity(), sharedPreferences) { state ->
                if (state != RateState.CANCEL) showToast(R.string.have_rated)
                requireActivity().finish()
                exit(0)
            }
        } else {
            requireActivity().finish()
            exit(0)
        }
        return true
    }
}