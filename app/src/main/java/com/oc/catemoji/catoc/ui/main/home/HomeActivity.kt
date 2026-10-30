package com.oc.catemoji.catoc.ui.main.home

import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.bumptech.glide.Glide
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.SharedHomeData
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.toSettingFromHome
import com.oc.catemoji.catoc.core.extention.toInformation
import com.oc.catemoji.catoc.core.extention.toPlayFromHome
import com.oc.catemoji.catoc.core.extention.toProfileFromHome
import com.oc.catemoji.catoc.core.extention.toTrainingFromHome
import com.oc.catemoji.catoc.core.helper.RateHelper.showRateDialog
import com.oc.catemoji.catoc.databinding.ActivityHomeBinding
import com.oc.catemoji.catoc.utils.DataLocal
import com.oc.catemoji.catoc.utils.state.RateState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class HomeActivity : BaseActivity<ActivityHomeBinding, HomeViewModel>(
    ActivityHomeBinding::inflate,
    HomeViewModel::class.java
) {

    private var countRate = 0
    private var openingInformation = false
    private var loadErrorShown = false

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
            btnAddBone.onClick {
                lifecycleScope.launch {
                    try {
                        viewModelActivity.addBones(100)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showSnackbar(
                            getString(R.string.home_add_bones_failed)
                        )
                    }
                }
            }
            btnEditProfile.onClick {
                toProfileFromHome()
            }
            imgBgBattle.onClick {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle(R.string.training_mode_title)
                    .setItems(arrayOf(
                        getString(R.string.training_mode_microphone),
                        getString(R.string.training_mode_touch),
                    )) { _, selection ->
                        toPlayFromHome(useMicrophone = selection == 0)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            txtTraining.onClick {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle(R.string.training_mode_title)
                    .setItems(arrayOf(
                        getString(R.string.training_mode_microphone),
                        getString(R.string.training_mode_touch),
                    )) { _, selection ->
                        when (selection) {
                            0 -> toTrainingFromHome(useMicrophone = true)
                            1 -> toTrainingFromHome()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        binding.btnAddBone.isEnabled = user != null

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
            state.dogPreview
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
