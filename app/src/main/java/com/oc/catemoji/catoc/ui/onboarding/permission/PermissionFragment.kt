package com.oc.catemoji.catoc.ui.onboarding.permission

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.lvt.ads.callback.InterCallback
import com.lvt.ads.callback.NativeCallback
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.select
import com.oc.catemoji.catoc.core.extention.toHomeFromPermission
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.StringHelper
import com.oc.catemoji.catoc.databinding.FragmentPermissionBinding
import com.oc.catemoji.catoc.utils.key.RequestKey
import com.oc.catemoji.catoc.core.extention.checkPermissions
import com.oc.catemoji.catoc.core.extention.dp
import com.oc.catemoji.catoc.core.extention.goToSettings
import com.oc.catemoji.catoc.core.extention.requestPermission
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.helper.PermissionHelper
import com.oc.catemoji.catoc.core.helper.PermissionRequestHelper
import com.oc.catemoji.catoc.databinding.FragmentSettingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PermissionFragment : BaseFragment<FragmentPermissionBinding, PermissionViewModel>(
    FragmentPermissionBinding::inflate, PermissionViewModel::class.java
), BackPressHandler {
    private var inter: InterstitialAd? = null
    override fun viewListener() {
        binding.swPermission.onClick(1500) { handlePermissionRequest(isStorage = true) }
        binding.swNotification.onClick(1500) { handlePermissionRequest(isStorage = false) }
        binding.tvContinue.onClick(1000) {
            Admob.getInstance().showInterAds(requireActivity(), inter, object : InterCallback() {
            override fun onNextAction() {
                super.onNextAction()
                handleContinue()
            }
        })}
    }
    override fun inflateBinding(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): FragmentPermissionBinding = FragmentPermissionBinding.inflate(inflater, container, false)

    override fun initView() {
        updateContinueMargin()
        Admob.getInstance().loadInterAds(requireContext(), getString(R.string.inter_per), object : InterCallback() {
            override fun onAdLoadSuccess(interstitialAd: InterstitialAd?) {
                super.onAdLoadSuccess(interstitialAd)
                inter = interstitialAd
            }
        })
        Admob.getInstance().loadNativeAd(requireContext(), getString(R.string.native_per), object : NativeCallback() {
            override fun onAdFailedToLoad() {
                super.onAdFailedToLoad()
                binding.nativeAds.gone()
            }

            override fun onNativeAdLoaded(nativeAd: NativeAd?) {
                super.onNativeAdLoaded(nativeAd)
                binding.nativeAds.visible()
                val adView =
                    LayoutInflater.from(requireContext()).inflate(R.layout.ads_native_big_btn_top, null) as NativeAdView
                binding.nativeAds.removeAllViews()
                binding.nativeAds.addView(adView)
                Admob.getInstance().pushAdsToViewCustom(nativeAd, adView)
            }
        })
        binding.setupActionBar()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            binding.btnStorage.visible()
            binding.btnNotification.gone()
        } else {
            binding.btnNotification.visible()
            binding.btnStorage.gone()
        }
        // cập nhật UI switch khi vào màn
        updatePermissionUI(requireContext().checkPermissions(PermissionHelper.storagePermission), true)
        updatePermissionUI(requireContext().checkPermissions(PermissionHelper.notificationPermission), false)
    }

    private fun FragmentPermissionBinding.setupActionBar() {
        actionBar.apply {
            tvStart.select()
            setTextActionBar(tvStart, getString(R.string.permission))
        }
    }
    private fun isNetworkAvailable(): Boolean {
        val cm = requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun updateContinueMargin() {
        val marginPx = if (!isNetworkAvailable()) {
            200.dp(requireContext())
        } else {
            10.dp(requireContext())
        }
        val params = binding.tvContinue.layoutParams as? ViewGroup.MarginLayoutParams
        params?.bottomMargin = marginPx  // hoặc topMargin tuỳ layout
        binding.tvContinue.layoutParams = params
    }
// ❌ Xóa 2 dòng này
// private var storageDenyCount = 0
// private var notificationDenyCount = 0

    private fun handlePermissionRequest(isStorage: Boolean) {
        val perms = if (isStorage) PermissionHelper.storagePermission
        else PermissionHelper.notificationPermission

        when {
            requireContext().checkPermissions(perms) ->
                showToast(if (isStorage) R.string.granted_storage else R.string.granted_notification)

            // ✅ Dùng ViewModel thay vì local count
            viewModel.shouldGoToSettings(isStorage) -> activity?.goToSettings()

            else -> requestPermission(
                perms,
                if (isStorage) RequestKey.STORAGE_PERMISSION_CODE
                else RequestKey.NOTIFICATION_PERMISSION_CODE
            )
        }
    }
    override fun onResume() {
        super.onResume()
        // ✅ Cập nhật lại UI khi quay về từ Settings hoặc sau khi grant
        updatePermissionUI(
            requireContext().checkPermissions(PermissionHelper.storagePermission),
            true
        )
        updatePermissionUI(
            requireContext().checkPermissions(PermissionHelper.notificationPermission),
            false
        )
    }
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        when (requestCode) {
            RequestKey.STORAGE_PERMISSION_CODE -> {
                if (granted) {
                    viewModel.onStorageGranted()
                } else {
                    viewModel.onStorageDenied()
                }
                // ✅ Luôn update UI dù granted hay denied
                updatePermissionUI(granted, true)
            }
            RequestKey.NOTIFICATION_PERMISSION_CODE -> {
                if (granted) {
                    viewModel.onNotificationGranted()
                } else {
                    viewModel.onNotificationDenied()
                }
                // ✅ Luôn update UI dù granted hay denied
                updatePermissionUI(granted, false)
            }
        }
    }

    private fun updatePermissionUI(granted: Boolean, isStorage: Boolean) {
        val imageView = if (isStorage) binding.swPermission else binding.swNotification
        imageView.setImageResource(if (granted) R.drawable.switch_on else R.drawable.switch_off)
    }


    override fun observeData() {}

    override fun initText() {
        binding.actionBar.tvCenter.select()
        val textRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            R.string.to_access_13 else R.string.to_access
        binding.txtPermission.text = TextUtils.concat(
            createColoredText(R.string.allow, R.color.app_color),
            " ",
            createColoredText(R.string.app_name, R.color.app_color),
            " ",
            createColoredText(textRes, R.color.app_color)
        )
    }

    private fun handleContinue() {
        sharedPreferences.setPermissionScreen(true)
        toHomeFromPermission()
    }

    override fun bindViewModel() {}

    private fun createColoredText(
        @androidx.annotation.StringRes textRes: Int,
        @androidx.annotation.ColorRes colorRes: Int,
        font: Int = R.font.baloo2_bold
    ) = StringHelper.changeColor(requireContext(), getString(textRes), colorRes, font)

    override fun onBackPressed(): Boolean {
        requireActivity().finish()
        System.exit(0)
        return true
    }
}