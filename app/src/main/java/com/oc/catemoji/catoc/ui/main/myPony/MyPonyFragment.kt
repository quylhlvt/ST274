package com.oc.catemoji.catoc.ui.main.myPony

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.dialog.CreateNameDialog
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.checkPermissions
import com.oc.catemoji.catoc.core.extention.goToSettings
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.core.extention.toCleanSelections
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.PermissionRequestHelper
import com.oc.catemoji.catoc.data.model.mypony.MyAlbumModel
import com.oc.catemoji.catoc.databinding.FragmentMyPonyBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeFragment
import com.oc.catemoji.catoc.ui.main.myPony.adapter.MyAvatarAdapter
import com.oc.catemoji.catoc.ui.main.myPony.adapter.MyDesignAdapter
import com.oc.catemoji.catoc.ui.onboarding.permission.PermissionViewModel
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import com.oc.catemoji.catoc.utils.share.whatsapp.WhatsappSharingFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MyPonyFragment : WhatsappSharingFragment<FragmentMyPonyBinding, MyPonyViewModel>(
    FragmentMyPonyBinding::inflate, MyPonyViewModel::class.java
) {
    private val storageHelper = PermissionRequestHelper()

    private lateinit var myAvatarAdapter: MyAvatarAdapter
    private lateinit var myDesignAdapter: MyDesignAdapter
    private var pendingDownloadPaths: ArrayList<String> = arrayListOf()
    private val permissionViewModel: PermissionViewModel by activityViewModels()

    private val isAvatarTab = MutableStateFlow(true)

    companion object {
        private const val ADD_PACK_REQUEST = 200
        private const val MIN_STICKERS_WHATSAPP = 3
        private const val MAX_STICKERS_WHATSAPP = 30
    }

    private fun performBatchDownload() {
        viewModel.downloadFiles(requireContext(), pendingDownloadPaths)
        resetSelection()
    }

    // ── INIT ──────────────────────────────────────────────────────────────────
    private fun initNativeCollab() {
        Log.d(
            "NativeCollab",
            "🔄 initNativeCollab called, stack: ${Thread.currentThread().stackTrace[3]}"
        )
        Admob.getInstance().loadNativeCollapNotBanner(
            requireContext(), getString(R.string.native_cl_creation), binding.flNativeCollab
        )
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

    override fun initView() {
        initNativeCollab()
        Admob.getInstance().loadNativeAd(
            requireContext(),
            getString(R.string.native_creation),
            binding.nativeAds,
            R.layout.ads_native_banner
        )

        binding.apply {
            tvWhatApp.isSelected = true
            tvTelegram.isSelected = true
            tvShare.isSelected = true
            tvDownload.isSelected = true
        }
        setupActionBar()
        setupTabs()
        setupRecyclerViews()
        setupBottomButtons()
        setupTouchListenerForResetSelection()
        loadAvatarData()
    }

    private fun setupActionBar() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.my_creation1))
            setImageActionBar(btnActionBarNextToRight1, R.drawable.ic_delete_all)
            setImageActionBar(btnActionBarRight1, R.drawable.ic_select_all)
            btnActionBarNextToRight1.invisible()
            btnActionBarRight1.invisible()
        }
    }

    private fun setupTabs() {
        binding.btnMyAvatar.onClick { switchTab(true) }
        binding.btnMyDesign.onClick { switchTab(false) }
    }

    private fun switchTab(isAvatar: Boolean) {
        isAvatarTab.value = isAvatar
        applyTabUI(isAvatar)
        resetSelection()
    }

    private fun applyTabUI(isAvatar: Boolean) {
        binding.apply {
            if (isAvatar) {
                imvFocusMyDesign.setImageResource(R.drawable.bg_btn_type_unselected)
                imvFocusMyAvatar.setImageResource(R.drawable.bg_btn_type_selected)
                recycleAvatar.visible()
                recycleDesign.gone()
                updateEmptyState(myAvatarAdapter.items.isEmpty())
                // ❌ Bỏ loadAvatarData() — dùng StateFlow
            } else {
                imvFocusMyDesign.setImageResource(R.drawable.bg_btn_type_selected)
                imvFocusMyAvatar.setImageResource(R.drawable.bg_btn_type_unselected)
                recycleAvatar.gone()
                recycleDesign.visible()
                updateEmptyState(myDesignAdapter.items.isEmpty())
                loadDesignData() // Design vẫn load thủ công vì không có StateFlow
            }
        }
    }

    private fun setupRecyclerViews() {
        myAvatarAdapter = MyAvatarAdapter(requireContext()).apply {
            onItemClick = { item ->
                showInter {
                    handleItemClick(item.path, true, 1, item.idEdit)
                }
            }
            onLongClick = { position -> handleLongClick(position, true) }
            onItemTick = { position -> toggleSelection(position, true) }
            onEditClick = { idEdit ->
                showInter {
                    navigateToEdit(idEdit)
                }
            }
            onDeleteClick = { path -> confirmDelete(arrayListOf(path), true) }
        }
        binding.recycleAvatar.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = myAvatarAdapter
            itemAnimator = null
        }

        myDesignAdapter = MyDesignAdapter().apply {
            onItemClick = { path ->    showInter {handleItemClick(path, false, 2, "0") }}
            onLongClick = { position -> handleLongClick(position, false) }
            onItemTick = { position -> toggleSelection(position, false) }
            onDeleteClick = { path -> confirmDelete(arrayListOf(path), false) }
        }
        binding.recycleDesign.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = myDesignAdapter
            itemAnimator = null
        }
    }

    private fun setupBottomButtons() {
        binding.apply {
            btnWhatsapp.onClick { handleWhatsAppShare() }
            btnTelegram.onClick { handleTelegramShare() }
            btnDownload.onClick { handleDownload() }
            btnShare.onClick { handleShare() }
            actionBar.btnActionBarRight1.onClick { handleSelectAll() }
            actionBar.btnActionBarNextToRight1.onClick { handleDeleteSelected() }  // ✅ thêm

        }
    }

    private fun handleShare() {
        val selected = getSelectedItems()
        if (selected.isEmpty()) {
            showToast(R.string.please_select_an_image); return
        }
        val paths = selected.map { it.path }.filter { it.isNotEmpty() }
        if (paths.isEmpty()) {
            showToast(R.string.please_select_an_image); return
        }

        val uris = ArrayList(paths.map { path ->
            androidx.core.content.FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.provider", java.io.File(path)
            )
        })

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        startActivity(Intent.createChooser(intent, getString(R.string.share)))
        resetSelection()
    }

    private fun setupTouchListenerForResetSelection() {
        val touchListener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_UP && rv.findChildViewUnder(e.x, e.y) == null) {
                    resetSelection()
                    return true
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}
        }
        binding.recycleAvatar.addOnItemTouchListener(touchListener)
        binding.recycleDesign.addOnItemTouchListener(touchListener)
    }

    // ── OBSERVE ───────────────────────────────────────────────────────────────
    override fun observeData() {
        // ✅ Chỉ dùng 1 nguồn duy nhất cho avatar
        viewLifecycleOwner.lifecycleScope.launch {
            viewModelActivity.customizedCharacters.collect { customized ->
                val list = customized.filter {
                        it.imageSave.isNotEmpty() && File(it.imageSave).exists()
                    }.sortedByDescending { it.createdAt } // ← dùng createdAt đã fix
                    .map { MyAlbumModel(path = it.imageSave, idEdit = it.id, type = 1) }

                myAvatarAdapter.submitList(list)
                if (isAvatarTab.value) updateEmptyState(list.isEmpty())
                updateSelectionUI()
            }
        }

        // Design giữ nguyên
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.myDesignList.collect { list ->
                myDesignAdapter.submitList(list)
                if (!isAvatarTab.value) updateEmptyState(list.isEmpty())
                updateSelectionUI()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.downloadState.collect { state ->
                when (state) {
                    MyPonyViewModel.DownloadState.SUCCESS -> showToast(
                        getString(
                            R.string.download_success, getString(R.string.app_name)
                        )
                    )

                    MyPonyViewModel.DownloadState.ERROR -> showToast(R.string.download_failed_please_try_again_later)

                    else -> {}
                }
            }
        }
    }

    // ── UI HELPERS ────────────────────────────────────────────────────────────

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.noItem.isVisible = isEmpty
    }

    private fun updateSelectionUI() {
        val currentList = if (isAvatarTab.value) myAvatarAdapter.items else myDesignAdapter.items
        val hasSelection = currentList.any { it.isShowSelection }
        val allSelected = currentList.isNotEmpty() && currentList.all { it.isSelected }
        val selectedCount = currentList.count { it.isSelected }

        binding.actionBar.apply {
            if (hasSelection) {
                btnActionBarNextToRight1.visible()
                btnActionBarRight1.visible()

                btnActionBarRight1.setImageResource(
                    if (allSelected) R.drawable.ic_select_all else R.drawable.ic_not_select_all
                )
            } else {
                btnActionBarNextToRight1.invisible()
                btnActionBarRight1.invisible()

            }
        }

        if (hasSelection) {
            binding.lnlBottom.visible()
            if (isAvatarTab.value) {
                binding.lnlBottomTop.visible()   // WhatsApp + Telegram
                binding.llBottom.gone()
            } else {
                binding.lnlBottomTop.gone()       // Ẩn WhatsApp + Telegram cho Design tab
                binding.llBottom.visible()

            }
        } else {
            binding.lnlBottom.gone()
        }
    }

    // ── DATA LOADING ──────────────────────────────────────────────────────────

    private fun loadAvatarData() = viewModel.loadMyAvatar(requireContext(), true)
    private fun loadDesignData() = viewModel.loadMyDesign(requireContext())

    // ── SELECTION ─────────────────────────────────────────────────────────────

    private fun handleItemClick(path: String, isAvatar: Boolean, type: Int, idEdit: String) {
//        val currentList = if (isAvatar) myAvatarAdapter.items else myDesignAdapter.items
//        if (currentList.any { it.isShowSelection }) {
//            val position = currentList.indexOfFirst { it.path == path }
//            if (position >= 0) toggleSelection(position, isAvatar)
//        } else {
//            navigateToView(path, type, idEdit)
//        }
        navigateToView(path, type, idEdit)
    }
// MyAvatarAdapter — long click gọi về Fragment


    // Fragment nhận và gọi ViewModel

    private fun handleLongClick(position: Int, isAvatar: Boolean) {
        val currentList = if (isAvatar) myAvatarAdapter.items else myDesignAdapter.items
        val updatedList = currentList.mapIndexed { index, item ->
            if (index == position) {
                item.copy(isSelected = true, isShowSelection = true)
            } else {
                item.copy(isShowSelection = true)
            }
        }
        if (isAvatar) {
            myAvatarAdapter.submitList(updatedList)
            setRecyclerBottomMargin(binding.recycleAvatar, 50) // ← thêm margin
        } else {
            myDesignAdapter.submitList(updatedList)
            setRecyclerBottomMargin(binding.recycleDesign, 50) // ← thêm margin
        }
        updateSelectionUI()
    }

    private fun setRecyclerBottomMargin(view: RecyclerView, dpValue: Int) {
        val px = (dpValue * resources.displayMetrics.density).toInt()
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            bottomMargin = px
            view.layoutParams = this
        }
    }

    private fun toggleSelection(position: Int, isAvatar: Boolean) {
        val currentList = if (isAvatar) myAvatarAdapter.items else myDesignAdapter.items
        val updatedList = currentList.mapIndexed { index, item ->
            if (index == position) item.copy(isSelected = !item.isSelected) else item
        }
        if (isAvatar) myAvatarAdapter.submitList(updatedList)
        else myDesignAdapter.submitList(updatedList)

        updateSelectionUI()
        if (updatedList.none { it.isSelected }) resetSelection()
    }

    private fun handleDeleteSelected() {
        val selected = getSelectedItems()
        if (selected.isEmpty()) {
            showToast(R.string.please_select_an_image); return
        }
        val paths = ArrayList(selected.map { it.path })
        confirmDelete(paths, isAvatarTab.value)
    }

    private fun handleSelectAll() {
        val currentList = if (isAvatarTab.value) myAvatarAdapter.items else myDesignAdapter.items
        val shouldSelectAll = !currentList.all { it.isSelected }
        val updatedList =
            currentList.map { it.copy(isSelected = shouldSelectAll, isShowSelection = true) }
        if (isAvatarTab.value) myAvatarAdapter.submitList(updatedList)
        else myDesignAdapter.submitList(updatedList)
        updateSelectionUI()
    }

    private fun resetSelection() {
        val avatarReset =
            myAvatarAdapter.items.map { it.copy(isSelected = false, isShowSelection = false) }
        val designReset =
            myDesignAdapter.items.map { it.copy(isSelected = false, isShowSelection = false) }
        myAvatarAdapter.submitList(avatarReset)
        myDesignAdapter.submitList(designReset)

        // Reset margin về 0
        setRecyclerBottomMargin(binding.recycleAvatar, 0)
        setRecyclerBottomMargin(binding.recycleDesign, 0)

        updateSelectionUI()
    }

    private fun getSelectedItems(): List<MyAlbumModel> =
        if (isAvatarTab.value) myAvatarAdapter.items.filter { it.isSelected }
        else myDesignAdapter.items.filter { it.isSelected }

    // ── NAVIGATION ────────────────────────────────────────────────────────────

    private fun navigateToView(path: String, type: Int, idEdit: String) {
        val action = MyPonyFragmentDirections.actionMyponyToView(path, idEdit, type)
        findNavController().navigate(action)
    }

    /**
     * Navigate sang CustomizeFragment ở chế độ Edit.
     *
     * Vấn đề: khi save từ template, ViewModelActivity.saveCharacterWithSelections() copy
     * character với id = UUID mới. Không có field "templateId" nào được lưu lại.
     *
     * Giải pháp: dùng [CustomModel.avatar] của customized character để tìm template gốc
     * có cùng avatar (template gốc KHÔNG thay đổi avatar, chỉ customized mới có imageSave riêng).
     *
     * Nếu project có field templateId trong CustomModel thì dùng trực tiếp field đó thay thế.
     */
    private fun navigateToEdit(idEdit: String) {
        val customized =
            viewModelActivity.customizedCharacters.value.firstOrNull { it.id == idEdit }
                ?: run { showToast("Character not found"); return }

        val templateIndex =
            viewModelActivity.getTemplateIndexForCustomized(idEdit).takeIf { it >= 0 }
                ?: run { showUnstableNetworkDialog(); return }

        val template = viewModelActivity.templates.value.getOrNull(templateIndex)

        // ✅ Thêm check: online template + mất mạng hoặc data chưa đủ
        if (template?.id?.startsWith("online_") == true) {
            val onlineTemplateCount =
                viewModelActivity.templates.value.count { it.id.startsWith("online_") }
            if (!isNetworkConnected(requireContext()) || onlineTemplateCount < 2) {
                showUnstableNetworkDialog()
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
        findNavController().navigate(R.id.action_mypony_to_custom, args)
    }

    // ── ACTIONS ───────────────────────────────────────────────────────────────

    private fun confirmDelete(paths: ArrayList<String>, isAvatar: Boolean) {
        showConfirmDialog(
            title = getString(R.string.delete),
            message = getString(R.string.are_you_sure_want_to_delete_this_item),
            onYes = {
                if (isAvatar) viewModel.deleteItem(requireContext(), paths)
                else viewModel.deleteItemDesign(paths, requireContext())
                resetSelection()
            },
            onNo = null
        )
    }

    private fun handleDownload() {
        val selected = getSelectedItems()
        if (selected.isEmpty()) {
            showToast(R.string.please_select_an_image); return
        }
        pendingDownloadPaths = ArrayList(selected.map { it.path })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performBatchDownload(); return
        }

        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        when {
            requireContext().checkPermissions(arrayOf(permission)) -> performBatchDownload()
            permissionViewModel.shouldGoToSettings(isStorage = true) -> activity?.goToSettings()
            else -> downloadPermissionLauncher.launch(arrayOf(permission))
        }
    }

    private val downloadPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                permissionViewModel.onStorageGranted()
                performBatchDownload()
            } else {
                permissionViewModel.onStorageDenied()
                // ✅ Chỉ toast, KHÔNG check goToSettings ở đây
                showToast(R.string.download_failed_please_try_again_later)
            }
        }
    // ── SHARE: chỉ dùng imageSave (ảnh render) ───────────────────────────────

    /**
     * Lấy đúng path để share.
     * - Avatar tab: dùng [MyAlbumModel.path] = customized.imageSave (ảnh render đã lưu)
     * - Design tab: dùng path trực tiếp
     * KHÔNG dùng customized.avatar (đó là thumbnail template gốc từ assets)
     */
    private fun getSharePaths(): List<String> =
        getSelectedItems().map { it.path }.filter { it.isNotEmpty() }

    // ── WHATSAPP ──────────────────────────────────────────────────────────────

    private fun handleWhatsAppShare() {
        val paths = getSharePaths()
        when {
            paths.isEmpty() -> {
                showToast(R.string.please_select_an_image); return
            }

            paths.size < MIN_STICKERS_WHATSAPP -> {
                showToast(R.string.limit_3_items); return
            }

            paths.size > MAX_STICKERS_WHATSAPP -> {
                showToast(R.string.limit_30_items); return
            }
        }
        // ✅ Dùng CreateNameDialog thay AlertDialog
        val dialog = CreateNameDialog(requireActivity())
        dialog.show()
        dialog.onYesClick = { packName ->
            dialog.dismiss()
            viewModel.addToWhatsapp(requireContext(), packName, ArrayList(paths)) { pack ->
                if (pack != null) {
                    addToWhatsapp(pack)
                    resetSelection()
                } else showToast("Failed to create sticker pack")
            }
        }
        dialog.onNoClick = { dialog.dismiss() }
        dialog.onDismissClick = { dialog.dismiss() }
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ADD_PACK_REQUEST) return
        when (resultCode) {
            android.app.Activity.RESULT_OK -> showToast("Sticker pack added successfully")
            android.app.Activity.RESULT_CANCELED -> {
                val err = data?.getStringExtra("validation_error")
                if (err != null) {
                    Log.e("MyPonyFragment", "Validation: $err"); showToast("Failed: $err")
                } else showToast("Cancelled")
            }
        }
    }

    // ── TELEGRAM ──────────────────────────────────────────────────────────────

    private fun handleTelegramShare() {
        val paths = getSharePaths()
        if (paths.isEmpty()) {
            showToast(R.string.please_select_an_image); return
        }
        viewModel.addToTelegram(requireContext(), ArrayList(paths))
        resetSelection()
    }

    // ── UTILITY ───────────────────────────────────────────────────────────────

    private fun showToast(resId: Int) =
        android.widget.Toast.makeText(requireContext(), resId, android.widget.Toast.LENGTH_SHORT)
            .show()

    private fun showToast(msg: String) =
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT)
            .show()

    // ── BASE OVERRIDES ────────────────────────────────────────────────────────

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.setOnClickListener {
            if (myAvatarAdapter.items.any { it.isShowSelection } || myDesignAdapter.items.any { it.isShowSelection }) {
                resetSelection()  // Thoát selection mode, KHÔNG navigate
            } else {
                findNavController().navigateUp()
            }
        }
    }

    override fun bindViewModel() {}

    override fun inflateBinding(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): FragmentMyPonyBinding = FragmentMyPonyBinding.inflate(inflater, container, false)

    override fun onResume() {
        super.onResume()
        applyTabUI(isAvatarTab.value)
        if (!isAvatarTab.value) {
            loadDesignData()
        }
    }
}