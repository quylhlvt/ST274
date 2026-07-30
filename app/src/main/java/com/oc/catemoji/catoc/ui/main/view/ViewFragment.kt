package com.oc.catemoji.catoc.ui.main.view

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.checkPermissions
import com.oc.catemoji.catoc.core.extention.dp
import com.oc.catemoji.catoc.core.extention.goToSettings
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.logEventSocial
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.onClick2
import com.oc.catemoji.catoc.core.extention.safeNavigate
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.toCleanSelections
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.PermissionRequestHelper
import com.oc.catemoji.catoc.databinding.FragmentViewBinding
import com.oc.catemoji.catoc.ui.main.add_character.DesignSource
import com.oc.catemoji.catoc.ui.main.customize.CustomizeFragment
import com.oc.catemoji.catoc.ui.onboarding.permission.PermissionViewModel
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class ViewFragment : BaseFragment<FragmentViewBinding, ViewViewModel>(
    FragmentViewBinding::inflate,
    ViewViewModel::class.java
) {
    private var isWaitingDataDialogShown = false
    private val storageHelper = PermissionRequestHelper()
    private var isReturningFromExternalScreen = false

    private val permissionViewModel: PermissionViewModel by activityViewModels()

    private var currentImagePath: String = ""
    private val imagePath: String by lazy { arguments?.getString("imagePath") ?: "" }
    private val imageType: Int by lazy { arguments?.getInt("imageType", 0) ?: 0 }
    private val idEdit: String by lazy { arguments?.getString("idEdit") ?: "" }
    private val designSource: String by lazy {
        arguments?.getString(DesignSource.ARG_SOURCE) ?: DesignSource.CUSTOM
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentViewBinding = FragmentViewBinding.inflate(inflater, container, false)

    override fun initView() {
        currentImagePath = imagePath
        binding.apply {
            setImageActionBar(actionBar.btnActionBarLeft, R.drawable.back_app)
            txtRight.isSelected = true
            txtLeft.isSelected = true
            txtLeft2.isSelected = true
            txtRight2.isSelected = true
            txtLeft3.isSelected = true
            txtRight3.isSelected = true

            when (imageType) {
                // ── Type 0: Từ AddCharacter — success screen ──────────────────
                0 -> {
                    frame.visible()
                    frame2.gone()
                    loadImage(requireContext(), imagePath, imvImage)
                    linearView.gone()
                    lnlBottom.visible()
                    lnlBottom.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = 5.dp(requireContext())
                    }
                    nativeAds.visible()
                    val successAdUnitId = if (designSource == DesignSource.STUDIO) {
                        R.string.native_successCatStudio
                    } else {
                        R.string.native_success
                    }
                    Admob.getInstance().loadNativeAd(
                        requireContext(),
                        getString(successAdUnitId),
                        binding.nativeAds,
                        R.layout.ads_native_big_btn_top
                    )
                    tvSuccess.visible()
                    setTextActionBar(
                        binding.actionBar.tvCenter,
                        getString(R.string.successful)
                    )
                    setImageActionBar(actionBar.btnActionBarNextToRight, R.drawable.ic_share)
                    setImageActionBar(actionBar.btnActionBarRight, R.drawable.ic_home)
                    // ✅ Show 2 nút bottom
                    txtLeft.apply { visible(); text = getString(R.string.my_creation1) }
                    txtRight.apply { visible(); text = getString(R.string.download) }
                }
                // ── Type 1: Avatar từ MyPony ──────────────────────────────────
                1 -> {
                    frame.gone()
                    frame2.visible()
                    loadImage(requireContext(), imagePath, imvImage2)
                    linearView.visible()
                    lnlBottom.gone()
                    tvSuccess.gone()
                    nativeAds.gone()
                    initNativeCollab()
                    txtLeft3.text = getString(R.string.share)
                    setImageActionBar(actionBar.btnActionBarRight, R.drawable.ic_delete)
                    setImageActionBar(actionBar.btnActionBarNextToRight, R.drawable.ic_edit1)
                }
                // ── Type 2: Design từ MyPony ──────────────────────────────────
                2 -> {
                    frame.gone()
                    frame2.visible()
                    loadImage(requireContext(), imagePath, imvImage2)
                    linearView.visible()
                    lnlBottom.gone()
                    tvSuccess.gone()
                    nativeAds.gone()
                    initNativeCollab()
                    txtLeft3.text = getString(R.string.share)
                    setImageActionBar(actionBar.btnActionBarRight, R.drawable.ic_delete)
                }
            }
        }
    }

    private fun initNativeCollab() {
        // Native collapsible detail chỉ dùng cho ảnh mở từ My Creation.
        if (imageType !in 1..2 || !isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        binding.flNativeCollab.visible()
        Admob.getInstance().loadNativeCollapNotBanner(
            requireContext(),
            getString(R.string.native_cl_detail),
            binding.flNativeCollab
        )
    }

    private fun disableNativeCollabOverlay() {
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
        binding.flNativeCollab.gone()
    }

    override fun onFragmentStart() {
        if (!isAdded || isDetached) return
        initNativeCollab()
    }

    override fun onFragmentStop() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }

    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarLeft.onClick {
                findNavController().navigateUp()
            }

            when (imageType) {
                // ── Type 0: Từ AddCharacter ───────────────────────────────────
                0 -> {
                    // Home button

                    actionBar.btnActionBarRight.onClick {
                        showInter{
                            findNavController().navigate(
                                R.id.homeFragment,
                                null,
                                androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.homeFragment, true)
                                    .build()
                            )
                        }

                    }
                    actionBar.btnActionBarNextToRight.onClick2 {
                        shareImage()
                    }
                    // ✅ MyPony button
                    btnBottomLeft.onClick {
                        showInter {
                            findNavController().navigate(
                                R.id.myPony,
                                null,
                                androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.homeFragment, false)
                                    .build()
                            )
                        }

                    }
                    // ✅ Download button
                    btnBottomRight.onClick2 { downloadImage() }
                }

                // ── Type 1: Avatar từ MyPony ──────────────────────────────────
                1 -> {
                    actionBar.btnActionBarRight.onClick { confirmDelete() }
                    actionBar.btnActionBarNextToRight.onClick {
                        if (ensureEditItemExists()) {
                            navigateToEdit()
                        }
                    }
                    btnBottomLeft2.onClick2 { shareToSocialApp(FACEBOOK_PACKAGE) }
                    btnBottomRight2.onClick2 { shareToSocialApp(INSTAGRAM_PACKAGE) }
                    btnBottomLeft3.onClick2 { shareImage() }
                    btnBottomRight3.onClick2 { downloadImage() }
                }

                // ── Type 2: Design từ MyPony ──────────────────────────────────
                2 -> {
                    actionBar.btnActionBarRight.onClick { confirmDelete() }
                    btnBottomLeft2.onClick2 {
                        val avatarUrl = viewModelActivity.customizedCharacters.value
                            .firstOrNull { it.id == idEdit }
                            ?.avatar
                            .orEmpty()
                        val dataName = Uri.parse(avatarUrl).pathSegments
                            .dropLast(1)
                            .lastOrNull()
                            .orEmpty()
                        logEventSocial(
                            "click_FaceBook",
                            "click_FaceBook_$dataName",
                            avatarUrl
                        )
                        Log.d("logevenfb", "click_FaceBook_$dataName -- ${avatarUrl}")
                        shareToSocialApp(FACEBOOK_PACKAGE)
                    }
                    btnBottomRight2.onClick2 {
                        val avatarUrl = viewModelActivity.customizedCharacters.value
                            .firstOrNull { it.id == idEdit }
                            ?.avatar
                            .orEmpty()
                        val dataName = Uri.parse(avatarUrl).pathSegments
                            .dropLast(1)
                            .lastOrNull()
                            .orEmpty()
                        logEventSocial(
                            "click_Instagram",
                            "click_Instagram_$dataName",
                            avatarUrl
                        )
                        Log.d("logevenfb", "click_Instagram_$dataName -- ${avatarUrl}")
                        shareToSocialApp(INSTAGRAM_PACKAGE) }
                    btnBottomLeft3.onClick2 { shareImage() }
                    btnBottomRight3.onClick2 { downloadImage() }
                }
            }
        }
    }

    private fun shareImage() {
        if (imagePath.isEmpty()) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            java.io.File(imagePath)
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun shareToSocialApp(packageName: String) {
        if (imagePath.isEmpty()) return

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            File(imagePath)
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            setPackage(packageName)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("shared_image", uri)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        requireContext().grantUriPermission(
            packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        try {
            isReturningFromExternalScreen = true
            // Mở một intent mới cho mỗi lần bấm. Không dùng chung ActivityResultLauncher
            // vì Facebook/Instagram có thể chưa trả result sau luồng permission/Settings.
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            isReturningFromExternalScreen = false
            showToast(
                if (packageName == FACEBOOK_PACKAGE) {
                    getString(R.string.facebook_story_not_supported)
                } else {
                    getString(R.string.instagram_story_not_supported)
                }
            )
        }
    }

    /**
     * Facebook/Instagram có thể mở thêm màn cấp quyền/Settings. Khi quay lại,
     * khôi phục rõ ràng trạng thái touch của màn View và native-ad container.
     */
    private fun restoreViewInteractions(clearNativeOverlay: Boolean = false) {
        if (!isAdded || view == null) return

        if (clearNativeOverlay) {
            disableNativeCollabOverlay()
        }

        binding.root.isEnabled = true
        binding.actionBar.root.isEnabled = true
        binding.actionBar.btnActionBarLeft.isEnabled = true
        binding.actionBar.btnActionBarNextToRight.isEnabled = true
        binding.actionBar.btnActionBarRight.isEnabled = true
        binding.btnBottomLeft.isEnabled = true
        binding.btnBottomRight.isEnabled = true
        binding.btnBottomLeft2.isEnabled = true
        binding.btnBottomRight2.isEnabled = true
        binding.btnBottomLeft3.isEnabled = true
        binding.btnBottomRight3.isEnabled = true

        binding.root.requestLayout()
        binding.root.invalidate()
    }

// ViewFragment.kt

    // Thêm vào ViewFragment
    private fun downloadImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performDownload(); return
        }
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        when {
            requireContext().checkPermissions(arrayOf(permission)) -> performDownload()
            permissionViewModel.shouldGoToSettings(isStorage = true) -> {
                isReturningFromExternalScreen = true
                activity?.goToSettings()
            }
            else -> downloadPermissionLauncher.launch(arrayOf(permission))
        }
    }

    private val downloadPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                permissionViewModel.onStorageGranted()
                performDownload()
            } else {
                permissionViewModel.onStorageDenied()
                // ✅ Chỉ toast, KHÔNG check goToSettings ở đây
                // goToSettings sẽ được check ở downloadImage() lần nhấn tiếp theo
                showToast(getString(R.string.download_failed_please_try_again_later))
            }
        }

    private fun performDownload() {
        viewModel.downloadFile(requireContext(), imagePath) { success ->
            showToast(
                if (success) getString(R.string.download_success, getString(R.string.app_name))
                else getString(R.string.download_failed_please_try_again_later)
            )
        }
    }

    private fun confirmDelete() {
        showConfirmDialog(
            title = getString(R.string.delete),
            message = getString(R.string.are_you_sure_want_to_delete_this_item),
            onYes = {
                viewModel.deleteFile(
                    path = imagePath,
                    isAvatar = imageType == 1,
                    idEdit = idEdit,
                    onDone = {
                        findNavController().navigateUp()
                    }
                )
            },
            onNo = null
        )
    }

    private fun navigateToEdit() {
        if (idEdit.isEmpty() || imageType != 1) {
            showEditItemNotFoundDialog()
            return
        }

        val customized = viewModelActivity.customizedCharacters.value
            .firstOrNull { it.id == idEdit }
            ?: run {
                showEditItemNotFoundDialog()
                return
            }

        val templateIndex = viewModelActivity.getTemplateIndexForCustomized(idEdit)
            .takeIf { it >= 0 }
            ?: run {
                showEditDataUnavailableDialog()
                return
            }  // ✅ không tìm thấy template → có thể do chưa load online

        val template = viewModelActivity.templates.value.getOrNull(templateIndex)

        // ✅ Thêm check: online template + mất mạng + online templates < 2
        if (template?.id?.startsWith("online_") == true) {
            val onlineTemplateCount = viewModelActivity.templates.value
                .count { it.id.startsWith("online_") }
            if (!isInternetAvailable(requireContext()) || onlineTemplateCount < 2) {
                showEditDataUnavailableDialog()
                return
            }
        }

        val args = CustomizeFragment.newArgs(
            templateIndex = templateIndex,
            isEdit = true,
            customizedId = idEdit,
            savedSelections = customized.selections.toCleanSelections(),
            isFlipped = customized.isFlipped
        )
        showInter {
            findNavController().safeNavigate(R.id.action_view_to_customize, args)
        }
    }

    private fun ensureEditItemExists(): Boolean {
        val exists = idEdit.isNotBlank() &&
            imageType == 1 &&
            viewModelActivity.customizedCharacters.value.any { it.id == idEdit }
        if (!exists) showEditItemNotFoundDialog()
        return exists
    }

    companion object {
        private const val FACEBOOK_PACKAGE = "com.facebook.katana"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val EXTERNAL_SCREEN_RESTORE_DELAY_MS = 500L
    }

    private fun showEditDataUnavailableDialog() {
        if (isInternetAvailable(requireContext()) && viewModelActivity.hasCompleteOnlineCatalogue()) {
            showEditItemNotFoundDialog()
            return
        }

        if (!isInternetAvailable(requireContext()) || !isNetworkConnected(requireContext())) {
            showUnstableNetworkDialog()
            return
        }

        if (!viewModelActivity.isFetchingOnlineFlow.value) {
            viewModelActivity.fetchOnlineTemplates()
        }
        if (!isWaitingDataDialogShown) {
            isWaitingDataDialogShown = true
            showLoadingDataDialog { isWaitingDataDialogShown = false }
        }
    }

    private fun showEditItemNotFoundDialog() {
        showOkDialog(
            title = getString(R.string.error),
            message = getString(R.string.errorcontent)
        )
    }

    private fun showToast(msg: String) =
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT)
            .show()

    override fun observeData() {
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>("updated_image_path")
            ?.observe(viewLifecycleOwner) { newPath ->
                if (newPath.isNullOrEmpty()) return@observe
                currentImagePath = newPath
                val targetImage = if (imageType == 0) binding.imvImage else binding.imvImage2
                loadImage(requireContext(), currentImagePath, targetImage)
            }
    }

    override fun onResume() {
        super.onResume()
        if (!isReturningFromExternalScreen) return

        // Facebook không bảo đảm trả Activity result (cả khi cho phép lẫn từ chối).
        // Khôi phục theo lifecycle của app, sau khi View thực sự quay lại foreground.
        requireActivity().hideNavigation(true)
        binding.root.post {
            restoreViewInteractions()
            requireActivity().hideNavigation(true)
        }
        binding.root.postDelayed({
            if (!isAdded || view == null) return@postDelayed
            restoreViewInteractions()
            requireActivity().hideNavigation(true)
            isReturningFromExternalScreen = false
        }, EXTERNAL_SCREEN_RESTORE_DELAY_MS)
    }

    override fun bindViewModel() {}
}
