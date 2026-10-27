package com.oc.catemoji.catoc.ui.onboarding.permission

import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import android.view.ViewGroup
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.*
import com.oc.catemoji.catoc.core.helper.PermissionHelper
import com.oc.catemoji.catoc.core.helper.StringHelper
import com.oc.catemoji.catoc.databinding.ActivityPermissionBinding
import com.oc.catemoji.catoc.utils.key.RequestKey
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionActivity : BaseActivity<ActivityPermissionBinding, PermissionViewModel>(
    ActivityPermissionBinding::inflate,
    PermissionViewModel::class.java
) {

    override fun viewListener() {
        val currentView = binding.root
        binding.swPermission.onClick(1500) { handlePermissionRequest(isStorage = true) }
        binding.swNotification.onClick(1500) { handlePermissionRequest(isStorage = false) }
        binding.tvContinue.onClick(1000) {
//            Admob.getInstance().showInterAds(requireActivity(), inter, object : InterCallback() {
//            override fun onNextAction() {
//                super.onNextAction()
//                if (!isAdded || view !== currentView) return
            handleContinue()
//            }
//        })
        }
    }

    override fun initView() {
//        updateContinueMargin()

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

    private fun ActivityPermissionBinding.setupActionBar() {
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
            resources.getDimensionPixelSize(R.dimen.dimension_200)
        } else {
            resources.getDimensionPixelSize(R.dimen.dimension_10)
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
            viewModel.shouldGoToSettings(isStorage) -> goToSettings()

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

        val textRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            R.string.to_access_13
        } else {
            R.string.to_access
        }

        binding.txtPermission.text = listOf(
            getString(R.string.allow),
            getString(R.string.app_name),
            getString(textRes)
        ).joinToString(" ")
    }

    private fun handleContinue() {
        sharedPreferences.setPermissionScreen(true)
        if (sharedPreferences.isInformationScreen()) {
            toHomeFromPermission()
        } else {
            toInformation()
        }
    }

    override fun bindViewModel() {}

    private fun createColoredText(
        @androidx.annotation.StringRes textRes: Int,
        @androidx.annotation.ColorRes colorRes: Int,
        font: Int = R.font.baloo2_bold
    ) = StringHelper.changeColor(requireContext(), getString(textRes), colorRes, font)

    override fun onScreenBackPressed(): Boolean {
        finishAffinity()
        return true
    }
}
