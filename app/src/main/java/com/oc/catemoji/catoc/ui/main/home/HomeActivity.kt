package com.oc.catemoji.catoc.ui.main.home

import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.bumptech.glide.Glide
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.SharedHomeData
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.dialog.PlayModeDialog
import com.oc.catemoji.catoc.core.dialog.GamePrompt
import com.oc.catemoji.catoc.core.dialog.GamePromptDialog
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.toSettingFromHome
import com.oc.catemoji.catoc.core.extention.toDogSkinFromHome
import com.oc.catemoji.catoc.core.extention.toInformation
import com.oc.catemoji.catoc.core.extention.toPlayFromHome
import com.oc.catemoji.catoc.core.extention.toProfileFromHome
import com.oc.catemoji.catoc.core.extention.toTrainingFromHome
import com.oc.catemoji.catoc.core.helper.RateHelper.showRateDialog
import com.oc.catemoji.catoc.databinding.ActivityHomeBinding
import com.oc.catemoji.catoc.utils.DataLocal
import com.oc.catemoji.catoc.utils.state.RateState
import com.oc.catemoji.catoc.ui.onboarding.permission.MicrophonePermissionRequest
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
    private var playModeDialog: PlayModeDialog? = null
    private var boneRewardDialog: GamePromptDialog? = null
    private val microphonePermission = MicrophonePermissionRequest(this) { granted ->
        viewModel.onMicrophonePermissionResult(granted)?.let { destination ->
            dismissModeDialog()
            openPlay(destination, useMicrophone = true)
        }
    }

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
            btnDogSkin.onClick { toDogSkinFromHome() }
            btnAddBone.onClick {
                showBoneRewardDialog()
            }
            btnEditProfile.onClick {
                toProfileFromHome()
            }
            imgBgBattle.onClick {
                viewModel.showModeSelection(HomePlayDestination.BATTLE)
                showModeDialog()
            }
            txtTraining.onClick {
                viewModel.showModeSelection(HomePlayDestination.TRAINING)
                showModeDialog()
            }
        }
        if (viewModel.modeDestination != null) showModeDialog()
    }

    private fun showModeDialog() {
        if (playModeDialog?.isShowing == true) return
        playModeDialog = PlayModeDialog(this,
            onTap = {
                viewModel.selectMode()?.let { destination ->
                    dismissModeDialog()
                    openPlay(destination, useMicrophone = false)
                }
            },
            onVoice = {
                if (viewModel.requestVoiceMode()) microphonePermission.request()
            },
            onClose = {
                viewModel.closeModeSelection()
                dismissModeDialog()
            },
        ).also { it.show() }
    }

    private fun openPlay(destination: HomePlayDestination, useMicrophone: Boolean) {
        when (destination) {
            HomePlayDestination.BATTLE -> toPlayFromHome(useMicrophone)
            HomePlayDestination.TRAINING -> toTrainingFromHome(useMicrophone)
        }
    }

    private fun dismissModeDialog() {
        playModeDialog?.dismiss()
        playModeDialog = null
    }

    private fun showBoneRewardDialog() {
        if (viewModel.boneRewardBusy.value || boneRewardDialog?.isShowing == true) return
        boneRewardDialog = GamePromptDialog(this, GamePrompt.WATCH_ADS,
            onConfirm = {
                boneRewardDialog?.dismiss()
                boneRewardDialog = null
                viewModel.claimBoneReward(viewModelActivity::addBones)
            },
            onCancel = {
                boneRewardDialog?.dismiss()
                boneRewardDialog = null
            },
        ).also { it.show() }
    }

    override fun onScreenDestroyed() {
        dismissModeDialog()
        boneRewardDialog?.dismiss()
        boneRewardDialog = null
        microphonePermission.dispose()
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.boneRewardBusy.collect { busy ->
                        binding.btnAddBone.isEnabled = binding.txtName.isEnabled && !busy
                        binding.btnAddBone.alpha = if (busy) 0.5f else 1f
                    }
                }
                launch {
                    viewModel.boneRewardSaveFailed.collect { failed ->
                        if (failed) {
                            showSnackbar(getString(R.string.home_add_bones_failed))
                            viewModel.consumeBoneRewardError()
                        }
                    }
                }
                viewModelActivity.homeState.collect { state ->
                    when (state) {
                        RoomLoadState.Loading -> {
                            binding.txtName.isEnabled = false
                            binding.btnAddBone.isEnabled = false
                        }

                        is RoomLoadState.Error -> {
                            binding.txtName.isEnabled = false
                            binding.btnAddBone.isEnabled = false

                            if (!loadErrorShown) {
                                loadErrorShown = true
                                showSnackbar(
                                    getString(R.string.home_profile_load_failed)
                                )
                            }
                        }

                        is RoomLoadState.Success -> {
                            renderHome(state.data)
                        }
                    }
                }
            }
        }
    }

    private fun renderHome(state: SharedHomeData) {
        val user = state.user

        binding.txtName.isEnabled = user != null
        binding.btnAddBone.isEnabled = user != null && !viewModel.boneRewardBusy.value

        if (user == null) {
            if (!openingInformation) {
                openingInformation = true

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        sharedPreferences.setInformationScreen(false)
                    }
                    toInformation()
                }
            }
            return
        }

        binding.apply {
            txtName.text = user.name

            txtName.contentDescription = getString(
                R.string.home_name_description,
                user.name
            )

            txtCoinBone.text = user.coinbone.toString()

            txtBattleRank.text = getString(
                R.string.home_battle_rank_title,
                user.rank
            )

            txtCoinRank.text = user.rankbatter.toString()
            imgRank.setImageResource(DataLocal.rankList[(user.rank - 1).coerceIn(0, DataLocal.rankList.lastIndex)])

            imgRank.contentDescription = getString(
                R.string.home_rank_description,
                user.rank
            )
        }

        Glide.with(this)
            .load(user.imageAvatar.ifBlank { null })
            .placeholder(shimmer)
            .into(binding.imgProfileAvatar)

        loadOrClear(
            binding.imgFrameAvatar,
            user.imageFrame
        )

        loadOrClear(
            binding.imgAvatar,
            state.dogHomeImage
        )
    }
    private fun loadOrClear(view: ImageView, model: Any?) {
        if (model == null || (model is String && model.isBlank())) {
            Glide.with(this).clear(view)
            view.setImageDrawable(null)
        } else {
            Glide.with(this).load(model).into(view)
        }
    }

    override fun onScreenBackPressed(): Boolean {
        if (viewModel.boneRewardBusy.value) return true
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
    companion object {
        private val shimmer  = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

    }
}
