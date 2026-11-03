package com.oc.catemoji.catoc.ui.main.dogSkin

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.dialog.GamePrompt
import com.oc.catemoji.catoc.core.dialog.GamePromptDialog
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.ActivityDogSkinBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DogSkinActivity : BaseActivity<ActivityDogSkinBinding, DogSkinViewModel>(
    ActivityDogSkinBinding::inflate,
    DogSkinViewModel::class.java,
) {
    private val skinAdapter = DogSkinAdapter { viewModel.onSkinClicked(it) }
    private var boneRewardDialog: GamePromptDialog? = null

    override fun initView() {
        binding.recycleDogSkin.adapter = skinAdapter
        binding.btnAddBone.isEnabled = false
    }

    override fun viewListener() {
        binding.btnActionBarLeft.onClick { onBackPressedDispatcher.onBackPressed() }
        binding.btnAddBone.onClick { showBoneRewardDialog() }
    }

    private fun showBoneRewardDialog() {
        val state = viewModel.state.value
        if (!state.hasUser || state.loading || state.claimingReward || boneRewardDialog?.isShowing == true) return
        boneRewardDialog = GamePromptDialog(this, GamePrompt.WATCH_ADS,
            onConfirm = {
                boneRewardDialog?.dismiss()
                boneRewardDialog = null
                viewModel.claimBoneReward()
            },
            onCancel = {
                boneRewardDialog?.dismiss()
                boneRewardDialog = null
            },
        ).also { it.show() }
    }

    override fun onScreenBackPressed(): Boolean = viewModel.state.value.claimingReward

    override fun onScreenDestroyed() {
        boneRewardDialog?.dismiss()
        boneRewardDialog = null
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.txtCoinBone.text = state.coinbone.toString()
                    binding.btnAddBone.isEnabled = state.hasUser && !state.loading &&
                        !state.claimingReward && state.error == null
                    binding.btnAddBone.alpha = if (state.claimingReward) 0.5f else 1f
                    if (skinAdapter.items != state.items) skinAdapter.submitList(state.items)
                    state.error?.let {
                        showSnackbar(getString(it))
                        viewModel.clearError()
                    }
                }
            }
        }
    }
}
