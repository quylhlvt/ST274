package com.oc.catemoji.catoc.ui.main.home

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.toSettingFromHome
import com.oc.catemoji.catoc.core.extention.toInformation
import com.oc.catemoji.catoc.core.helper.RateHelper.showRateDialog
import com.oc.catemoji.catoc.databinding.ActivityHomeBinding
import com.oc.catemoji.catoc.databinding.DialogHomeNameBinding
import com.oc.catemoji.catoc.utils.state.RateState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HomeActivity : BaseActivity<ActivityHomeBinding, HomeViewModel>(
    ActivityHomeBinding::inflate,
    HomeViewModel::class.java
) {

    private var countRate = 0
    private var openingInformation = false
    private var loadErrorShown = false
    private var nameDialog: AlertDialog? = null
    private var nameInput: EditText? = null

    override fun initView() {
        binding.apply {
            txtBattleRank.isSelected = true
            txtCoinRank.isSelected = true
            txtDogSkin.isSelected = true
            txtName.isSelected = true
        }

        binding.btnEnd.apply {
            setImageActionBar(binding.btnEnd, R.drawable.ic_settings)
        }
    }
    override fun viewListener() {
        binding.apply {
            btnEnd.onClick { toSettingFromHome() }
            btnAddBone.onClick { viewModel.add100Bones() }
            txtName.onClick { showChangeNameDialog() }
        }

    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::renderHome)
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            HomeEvent.NAME_SAVED -> nameDialog?.dismiss()
                            HomeEvent.NAME_REQUIRED -> nameInput?.error = getString(R.string.profile_name_required)
                            HomeEvent.SAVE_FAILED -> showSnackbar(getString(R.string.profile_save_failed))
                            HomeEvent.BONE_ADD_FAILED -> showSnackbar(getString(R.string.home_add_bones_failed))
                        }
                    }
                }
                launch {
                    viewModel.renaming.collect { saving ->
                        nameInput?.isEnabled = !saving
                        nameDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !saving
                        nameDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !saving
                        nameDialog?.setCancelable(!saving)
                        nameDialog?.setCanceledOnTouchOutside(!saving)
                    }
                }
            }
        }
    }

    private fun renderHome(state: HomeUiState) {
        binding.txtName.isEnabled = !state.loading && !state.loadFailed && state.user != null
        binding.btnAddBone.isEnabled = !state.loading && !state.loadFailed && state.user != null
        if (state.loading) return
        if (state.loadFailed) {
            if (!loadErrorShown) {
                loadErrorShown = true
                showSnackbar(getString(R.string.home_profile_load_failed))
            }
            return
        }
        val user = state.user
        if (user == null) {
            if (!openingInformation) {
                openingInformation = true
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { sharedPreferences.setInformationScreen(false) }
                    toInformation()
                }
            }
            return
        }
        binding.txtName.text = user.name
        binding.txtName.contentDescription = getString(R.string.home_name_description, user.name)
        binding.txtCoinBone.text = user.coinbone.toString()
        binding.txtBattleRank.text = getString(R.string.home_battle_rank_title, user.rank)
        binding.txtCoinRank.text = user.rankbatter.toString()
        binding.imgRank.contentDescription = getString(R.string.home_rank_description, user.rank)

        Glide.with(this)
            .load(user.imageAvatar.ifBlank { null })
            .placeholder(R.drawable.image_avatar_infor)
            .error(R.drawable.image_avatar_infor)
            .into(binding.imgProfileAvatar)
        Glide.with(this)
            .load(user.imageFrame.ifBlank { null })
            .placeholder(R.drawable.img_frame_avatar)
            .error(R.drawable.img_frame_avatar)
            .into(binding.imgFrameAvatar)
        Glide.with(this)
            .load(state.dogPreview)
            .placeholder(R.drawable.a)
            .error(R.drawable.a)
            .into(binding.imgAvatar)
    }

    private fun showChangeNameDialog() {
        val user = viewModel.uiState.value.user ?: return
        if (nameDialog?.isShowing == true) return
        val dialogBinding = DialogHomeNameBinding.inflate(layoutInflater)
        nameInput = dialogBinding.editProfileName.apply {
            setText(user.name)
            setSelection(text?.length ?: 0)
        }
        nameDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_change_name)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).onClick {
                        viewModel.renameUser(dialogBinding.editProfileName.text.toString())
                    }
                }
                dialog.setOnDismissListener {
                    nameDialog = null
                    nameInput = null
                }
                dialog.show()
            }
    }

    override fun onScreenDestroyed() {
        nameDialog?.dismiss()
        nameDialog = null
        nameInput = null
        super.onScreenDestroyed()
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
