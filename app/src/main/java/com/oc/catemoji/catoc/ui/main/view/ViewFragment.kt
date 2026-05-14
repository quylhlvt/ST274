package com.oc.catemoji.catoc.ui.main.view

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.checkPermissions
import com.oc.catemoji.catoc.core.extention.goToSettings
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.safeNavigate
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.toCleanSelections
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.PermissionRequestHelper
import com.oc.catemoji.catoc.databinding.FragmentViewBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeFragment
import com.oc.catemoji.catoc.ui.onboarding.permission.PermissionViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class ViewFragment : BaseFragment<FragmentViewBinding, ViewViewModel>(
    FragmentViewBinding::inflate,
    ViewViewModel::class.java
) {
    private val storageHelper = PermissionRequestHelper()

    private val permissionViewModel: PermissionViewModel by activityViewModels()

    private var currentImagePath: String = ""
    private val imagePath: String by lazy { arguments?.getString("imagePath") ?: "" }
    private val imageType: Int    by lazy { arguments?.getInt("imageType", 0) ?: 0 }
    private val idEdit: String    by lazy { arguments?.getString("idEdit") ?: "" }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentViewBinding = FragmentViewBinding.inflate(inflater, container, false)

    override fun initView() {
        currentImagePath = imagePath
        binding.apply {
            setImageActionBar(actionBar.btnActionBarLeft, R.drawable.back_app)
            loadImage(requireContext(), imagePath, imvImage)
            txtRight.isSelected = true
            txtLeft.isSelected = true

            when (imageType) {
                // ── Type 0: Từ AddCharacter — success screen ──────────────────
                0 -> {
                    tvSuccess.visible()
                    setTextActionBar(
                        binding.actionBar.tvCenter,
                        getString(R.string.successful)
                    )
                    setImageActionBar(actionBar.btnActionBarNextToRight, R.drawable.ic_share)
                    setImageActionBar(actionBar.btnActionBarRight, R.drawable.ic_home)
                    // ✅ Show 2 nút bottom
                    txtLeft.apply  { visible(); text = getString(R.string.my_creation1) }
                    txtRight.apply { visible(); text = getString(R.string.download) }
                }
                // ── Type 1: Avatar từ MyPony ──────────────────────────────────
                1 -> {
                    txtLeft.text = getString(R.string.share)
                    setImageActionBar(actionBar.btnActionBarRight,       R.drawable.ic_delete)
                    setImageActionBar(actionBar.btnActionBarNextToRight, R.drawable.ic_edit1)
                }
                // ── Type 2: Design từ MyPony ──────────────────────────────────
                2 -> {
                    txtLeft.text = getString(R.string.share)
                    setImageActionBar(actionBar.btnActionBarRight, R.drawable.ic_delete)
                }
            }
        }
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
                        findNavController().navigate(
                            R.id.homeFragment,
                            null,
                            androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(R.id.homeFragment, true)
                                .build()
                        )
                    }
                    actionBar.btnActionBarNextToRight.onClick {
                        shareImage()
                    }
                    // ✅ MyPony button
                    btnBottomLeft.onClick {
                        findNavController().navigate(
                            R.id.myPony,
                            null,
                            androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(R.id.homeFragment, false)
                                .build()
                        )
                    }
                    // ✅ Download button
                    btnBottomRight.onClick { downloadImage() }
                }

                // ── Type 1: Avatar từ MyPony ──────────────────────────────────
                1 -> {
                    actionBar.btnActionBarRight.onClick       { confirmDelete() }
                    actionBar.btnActionBarNextToRight.onClick { navigateToEdit()  }
                    btnBottomLeft.onClick                     { shareImage()}
                    btnBottomRight.onClick                    { downloadImage() }
                }

                // ── Type 2: Design từ MyPony ──────────────────────────────────
                2 -> {
                    actionBar.btnActionBarRight.onClick       { confirmDelete() }
                    btnBottomLeft.onClick                     { shareImage() }
                    btnBottomRight.onClick                    { downloadImage() }
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
// ViewFragment.kt

    // Thêm vào ViewFragment
    private fun downloadImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { performDownload(); return }
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        when {
            requireContext().checkPermissions(arrayOf(permission)) -> performDownload()
            permissionViewModel.shouldGoToSettings(isStorage = true) -> activity?.goToSettings()
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
                    path     = imagePath,
                    isAvatar = imageType == 1,
                    idEdit   = idEdit,
                    onDone   = {
                        findNavController().navigateUp()
                    }
                )
            },
            onNo = null
        )
    }
    private fun navigateToEdit() {
        if (idEdit.isEmpty() || imageType != 1) return

        val customized = viewModelActivity.customizedCharacters.value
            .firstOrNull { it.id == idEdit }
            ?: run { showToast("Character not found"); return }

        val templateIndex = viewModelActivity.getTemplateIndexForCustomized(idEdit)
            .takeIf { it >= 0 }
            ?: run { showUnstableNetworkDialog(); return }  // ✅ không tìm thấy template → có thể do chưa load online

        val template = viewModelActivity.templates.value.getOrNull(templateIndex)

        // ✅ Thêm check: online template + mất mạng + online templates < 2
        if (template?.id?.startsWith("online_") == true) {
            val onlineTemplateCount = viewModelActivity.templates.value
                .count { it.id.startsWith("online_") }
            if (!isInternetAvailable(requireContext()) || onlineTemplateCount < 2) {
                showUnstableNetworkDialog()
                return
            }
        }

        val args = CustomizeFragment.newArgs(
            templateIndex   = templateIndex,
            isEdit          = true,
            customizedId    = idEdit,
            savedSelections = customized.selections.toCleanSelections(),
            isFlipped       = customized.isFlipped
        )
        findNavController().safeNavigate(R.id.action_view_to_customize, args)
    }

    private fun showToast(msg: String) =
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()

    override fun observeData() {
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>("updated_image_path")
            ?.observe(viewLifecycleOwner) { newPath ->
                if (newPath.isNullOrEmpty()) return@observe
                currentImagePath = newPath
                loadImage(requireContext(), currentImagePath, binding.imvImage)
            }
    }
    override fun bindViewModel() {}
}