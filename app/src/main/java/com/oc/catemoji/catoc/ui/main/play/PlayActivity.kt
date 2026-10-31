package com.oc.catemoji.catoc.ui.main.play

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.helper.BarkEffectRenderer
import com.oc.catemoji.catoc.core.custom.VoiceTrackDivider
import com.oc.catemoji.catoc.core.custom.BattleAvatarGrowth
import com.oc.catemoji.catoc.core.extention.toHome
import com.oc.catemoji.catoc.core.extention.toInformation
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.databinding.ActivityPlayBinding
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class PlayActivity : BaseActivity<ActivityPlayBinding, PlayViewModel>(
    ActivityPlayBinding::inflate, PlayViewModel::class.java,
) {
    override val retainScreenViewState = false
    private val useMicrophone get() = viewModel.useMicrophone
    private var lastMicrophoneStatus: MicrophoneStatus? = null
    private var entrancePreDraw: OneShotPreDrawListener? = null
    private var entrancePending = false
    private var renderedAppearance: Pair<String, String>? = null
    private var appearanceGeneration = 0
    private var imagesReady = false
    private var playAnimation: AnimatorSet? = null
    private var microphonePermissionPending = false
    private var waveformAvatarTarget: CustomTarget<Drawable>? = null
    private var backgroundPath: String? = null
    private var backgroundTarget: CustomViewTarget<View, Drawable>? = null
    private val badgeAdapter = BattleBadgeAdapter()
    private lateinit var barkEffects: BarkEffectRenderer
    private var rewardedAd: PlayRewardedAd? = null
    private var resultErrorSnackbar: Snackbar? = null
    private var voiceTrackDivider: VoiceTrackDivider? = null
    private var battleAvatarGrowth: BattleAvatarGrowth? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphonePermissionPending = false
        viewModel.onMicrophonePermissionResult(
            granted,
            blocked = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
        )
    }

    override fun initView() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.play))
            btnActionBarLeft.contentDescription = getString(R.string.navigate_back)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // The shared action bar already reserves its top space, as on Profile/Settings.
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }


        binding.contrainOver.isVisible = false
        barkEffects = BarkEffectRenderer(binding.root, binding.effectLayer)
        binding.dualWaveform.setOnPointScoredListener { player ->
            barkEffects.show(if (player) binding.avatar1 else binding.avatar2, mirrored = !player)
        }
        binding.recycleAchi.adapter = badgeAdapter
        binding.dualWaveform.restoreBattleProgress(viewModel.trackProgress)
        rewardedAd = PlayRewardedAd(this, viewModel::onRewardedAdBusyChanged, viewModel::claimEarnedBonus) {
            showToast(R.string.play_reward_unavailable)
        }
        loadOpponent()
        binding.namePlay1.isSelected = true
        binding.txtCoinRank1.isSelected = true
        binding.txtNameAvatar1.isSelected = true
        binding.txtTitleUnlock.isSelected =true
        binding.txtNextAchiUnlock.isSelected =true
        binding.txtTraining.isSelected =true
        binding.txtFindMatch.isSelected =true
        binding.txtNameTitleParticipation.isSelected =true
        binding.txtContent.isSelected =true
        binding.txtNameAvatar2.apply {
            isSelected = true
            setText(R.string.play_opponent_name)
        }
        binding.namePlay2.setText(R.string.play_opponent_name)
        binding.avatar2.contentDescription = getString(R.string.play_opponent_name)
        binding.avatar1.isEnabled = false
        binding.avatar2.isEnabled = false
        binding.dualWaveform.setOnBattleFinishedListener {
            binding.dualWaveform.stop()
            barkEffects.clear()
            viewModel.finishBattle(binding.dualWaveform.progress >= 1f)
            binding.avatar1.isEnabled = false
            binding.avatar2.isEnabled = false
        }
        val divider = VoiceTrackDivider(
            binding.dualWaveform, binding.changeVoice,
            binding.bgVoiceAvatar1, binding.bgVoiceAvatar2,
        )
        voiceTrackDivider = divider
        val growth = BattleAvatarGrowth(binding.avatar1, binding.avatar2)
        battleAvatarGrowth = growth
        binding.dualWaveform.setOnBattleProgressChangedListener { progress ->
            viewModel.trackProgress = binding.dualWaveform.progress
            divider.updateBattleProgress(progress)
            growth.updateBattleProgress(progress)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (binding.dualWaveform.isBattleFinished && !viewModel.isFinished) {
            viewModel.finishBattle(binding.dualWaveform.progress >= 1f)
        }
        binding.txtStartNumber.visibility = View.INVISIBLE
        entrancePending = !viewModel.introFinished
        binding.progressPlay.isVisible = true
        if (entrancePending) {
            binding.contrainFlash.visibility = View.VISIBLE
            binding.viewflash.visibility = View.VISIBLE
            binding.contrainPlay1.alpha = 0f
            binding.contrainPlay2.alpha = 0f
            binding.txtVs.alpha = 0f
        } else {
            showExitedState()
        }
    }

    override fun viewListener() {
        // Only the player's avatar accepts taps; the opponent scores on its timer.
        binding.avatar1.setOnClickListener { viewModel.addPlayerTap() }
        binding.avatar1.isClickable = !useMicrophone
        binding.avatar2.isClickable = false
        binding.avatar2.isFocusable = false
        binding.actionBar.btnActionBarLeft.onClick {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.btnNext.onClick { viewModel.advanceResult() }
        binding.btnNext2.onClick { viewModel.advanceResult() }
        binding.imgHomeParticipation.onClick {
            if (viewModel.canLeaveResult) toHome()
        }
        binding.btnSave.onClick {
            if (viewModel.canLeaveResult) {
                startActivity(Intent(this, PlayActivity::class.java)
                    .putExtra(EXTRA_USE_MICROPHONE, useMicrophone))
                finish()
            }
        }
        binding.btnAddBone.onClick {
            if (viewModel.canClaimBonus) rewardedAd?.show()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.microphone.collect(::renderMicrophone) }
                launch { viewModel.battleUiState.collect(::renderBattle) }
                launch { viewModel.resultUiState.collect(::renderResult) }
                launch { viewModel.effects.collect(barkEffects::setEffects) }
                launch {
                    viewModel.background.collect { path ->
                        if (path != null && path != backgroundPath) loadBackground(path)
                    }
                }
                launch {
                    viewModel.barkCount.collect { targetCount ->
                        var nextCount = viewModel.consumedBarks
                        binding.txtBarkCount.text = getString(R.string.play_bark_count, nextCount)
                        while (nextCount < targetCount && !viewModel.isFinished) {
                            if (binding.dualWaveform.isBattleFinished) break
                            nextCount++
                            viewModel.consumedBarks = nextCount
                            binding.txtBarkCount.text = getString(R.string.play_bark_count, nextCount)
                            binding.dualWaveform.addPlayerPoint()
                        }
                    }
                }
                launch {
                    viewModel.player.collect { state ->
                        if (isFinishing) return@collect
                        when (state) {
                            RoomLoadState.Loading -> binding.progressPlay.isVisible = true
                            is RoomLoadState.Error -> {
                                showToast(R.string.home_profile_load_failed)
                                finish()
                            }
                            is RoomLoadState.Success -> {
                                val user = state.data
                                if (user == null) toInformation() else renderPlayer(user)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadOpponent() {
        listOf(binding.avatar2, binding.imgAvatar2).forEach { image ->
            Glide.with(this).load(viewModel.opponentAvatar)
                .placeholder(R.drawable.image_avatar_infor).error(R.drawable.image_avatar_infor).into(image)
        }
        Glide.with(this).load(viewModel.opponentFrame).error(R.drawable.img_frame_avatar).into(binding.imgFrameAvatar2)
    }

    private fun renderBattle(state: PlayBattleUiState) = with(binding) {
        val number = state.countdownNumber
        txtStartNumber.visibility = if (number == null) View.INVISIBLE else View.VISIBLE
        if (number != null) {
            txtStartNumber.text = if (number == 0) getString(R.string.play_countdown_start)
                else getString(R.string.play_countdown_number, number)
        }
        avatar1.isEnabled = state.canTap
        avatar2.isEnabled = false
        if (!state.canPlay) dualWaveform.stop()
        else if (state.runTapOpponent && !dualWaveform.isBattleFinished) dualWaveform.showStaticWaveform()
        if (state.canPlay) requestMicrophoneAfterIntro()
    }

    private fun renderResult(state: PlayResultUiState) = with(binding) {
        txtOver.isVisible = state.showGameOver
        txtOver.isClickable = txtOver.isVisible
        contrainOver.isVisible = state.showResults
        achiContrain.isVisible = state.showBadges
        rankContrain.isVisible = state.showRank
        completeContrain.isVisible = state.showReward
        if (!state.isPlaying) {
            avatar1.isEnabled = false
            avatar2.isEnabled = false
            txtStartNumber.visibility = View.INVISIBLE
        }
        state.reward?.let { reward ->
            badgeAdapter.submitList(reward.badges)
            namneRankPre.text = getString(R.string.home_rank_description, reward.previousRank)
            namneRankNext.text = getString(R.string.home_rank_description, reward.nextRank)
            imgRankPre.setImageResource(DataLocal.rankList[reward.previousRankIconIndex])
            imgRankNext.setImageResource(DataLocal.rankList[reward.nextRankIconIndex])
            txtTraining.setText(reward.title)
            txtTraining.setGradientColors(
                ContextCompat.getColor(this@PlayActivity, reward.gradientStart),
                ContextCompat.getColor(this@PlayActivity, reward.gradientEnd),
            )
            Glide.with(this@PlayActivity).clear(imgBattleResult)
            imgBattleResult.setImageResource(reward.image)
            bgTitleWin.setImageResource(reward.imageBgTitel)
            txtCoinBone.text = getString(R.string.play_reward_amount, reward.bones)
            plusCoinRank.text = getString(R.string.play_reward_amount, reward.rankPoints)
            txtRewardMultiplier.setText(reward.bonusLabel)
        }
        btnAddBone.isEnabled = state.canClaimBonus
        btnAddBone.alpha = if (btnAddBone.isEnabled) 1f else 0.5f
        btnSave.isEnabled = state.canLeave
        imgHomeParticipation.isEnabled = state.canLeave
        progressPlay.isVisible = state.busy || (!imagesReady && state.isPlaying)
        if (state.saveFailed && resultErrorSnackbar == null) {
            resultErrorSnackbar = Snackbar.make(root, R.string.play_result_save_failed, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.play_retry) { viewModel.retryResultSave() }.also {
                    it.view.elevation = resources.getDimension(R.dimen.dimension_16)
                    it.show()
                }
        } else if (!state.saveFailed) {
            resultErrorSnackbar?.dismiss()
            resultErrorSnackbar = null
        }
    }

    override fun onScreenBackPressed(): Boolean {
        if (viewModel.isFinished) {
            if (viewModel.canLeaveResult) toHome()
            return true
        }
        return false
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicrophoneAfterIntro() {
        if (microphonePermissionPending || binding.dualWaveform.isBattleFinished || isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (viewModel.requestMicrophone(hasMicrophonePermission())) {
            microphonePermissionPending = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun renderMicrophone(state: MicrophoneUiState) {
        if (!useMicrophone) return
        val active = state.status == MicrophoneStatus.STARTING || state.status == MicrophoneStatus.LISTENING
        if (active && viewModel.battleUiState.value.canPlay) binding.dualWaveform.start() else binding.dualWaveform.stop()
        binding.dualWaveform.addRealAmplitude((state.level * 32767).roundToInt())
        if (state.status != lastMicrophoneStatus) {
            when (state.status) {
                MicrophoneStatus.PERMISSION_REQUIRED -> Snackbar.make(
                    binding.root, R.string.play_microphone_permission, Snackbar.LENGTH_INDEFINITE,
                ).setAction(R.string.play_microphone_settings) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }.show()
                MicrophoneStatus.ERROR -> Snackbar.make(
                    binding.root, state.error ?: R.string.play_speech_error, Snackbar.LENGTH_INDEFINITE,
                ).setAction(R.string.play_microphone_start) {
                    viewModel.retryMicrophone()
                    requestMicrophoneAfterIntro()
                }.show()
                else -> Unit
            }
        }
        lastMicrophoneStatus = state.status
    }

    private fun renderPlayer(state: PlayPlayerUiState) {
        val user = state.profile
        binding.dualWaveform.setTapOpponentInterval(state.opponentIntervalMs)
        binding.namePlay1.text = user.name
        binding.txtNameAvatar1.text = user.name
        binding.avatar1.contentDescription = user.name
        binding.txtCoinRank1.text = user.rankbatter.toString()
        binding.txtCoinRank2.text = user.rankbatter.toString()
        binding.imgRank2.setImageResource(DataLocal.rankList[state.rankIconIndex])
        binding.imgRank1.setImageResource(DataLocal.rankList[state.rankIconIndex])
        binding.imgRank1.contentDescription = getString(R.string.home_rank_description, user.rank)
        binding.frameAvatar1.contentDescription = getString(R.string.home_name_description, user.name)

        val appearance = user.imageAvatar to user.imageFrame
        if (renderedAppearance == appearance) {
            if (imagesReady) {
                renderResult(viewModel.resultUiState.value)
                scheduleEntrance()
            }
            return
        }
        renderedAppearance = appearance
        loadWaveformAvatar(user.imageAvatar)
        imagesReady = false
        val generation = ++appearanceGeneration
        var remainingImages = if (entrancePending) 3 else 1
        val onImageReady = {
            if (generation == appearanceGeneration && !isDestroyed) {
                remainingImages--
                if (remainingImages == 0) {
                    imagesReady = true
                    renderResult(viewModel.resultUiState.value)
                    scheduleEntrance()
                }
            }
        }
        loadAppearance(binding.avatar1, user.imageAvatar, R.drawable.image_avatar_infor, onImageReady)
        if (entrancePending) {
            loadAppearance(binding.imgAvatar1, user.imageAvatar, R.drawable.image_avatar_infor, onImageReady)
            loadAppearance(binding.imgFrameAvatar1, user.imageFrame, R.drawable.img_frame_avatar, onImageReady)
        }
    }

    private fun loadWaveformAvatar(path: String) {
        waveformAvatarTarget?.let { Glide.with(this).clear(it) }
        val target = object : CustomTarget<Drawable>(120, 120) {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (waveformAvatarTarget === this) binding.dualWaveform.setAvatar(resource)
            }
            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (waveformAvatarTarget === this) binding.dualWaveform.setAvatar(R.drawable.image_avatar_infor)
            }
            override fun onLoadCleared(placeholder: Drawable?) {
                if (waveformAvatarTarget === this) binding.dualWaveform.setAvatar(null)
            }
        }
        waveformAvatarTarget = target
        Glide.with(this).load(path.ifBlank { null }).dontAnimate().into(target)
    }

    private fun loadBackground(path: String) {
        if (isFinishing || isDestroyed) return
        backgroundPath = path
        backgroundTarget?.let { Glide.with(applicationContext).clear(it) }
        val target = object : CustomViewTarget<View, Drawable>(binding.root) {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                view.background = resource
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                view.background = errorDrawable
            }

            override fun onResourceCleared(placeholder: Drawable?) {
                view.background = placeholder
            }
        }
        backgroundTarget = target
        Glide.with(this).load(path).centerCrop().dontAnimate().into(target)
    }

    private fun loadAppearance(
        image: ImageView,
        path: String,
        @DrawableRes fallback: Int,
        onReady: () -> Unit,
    ) {
        Glide.with(this)
            .load(path.ifBlank { null })
            .placeholder(fallback)
            .error(fallback)
            .dontAnimate()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean,
                ): Boolean {
                    onReady()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?,
                    dataSource: DataSource, isFirstResource: Boolean,
                ): Boolean {
                    onReady()
                    return false
                }
            })
            .into(image)
    }

    private fun scheduleEntrance() {
        if (!entrancePending || !imagesReady || entrancePreDraw != null || isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        // Glide listeners run before the drawable reaches the ImageView.
        entrancePreDraw = binding.root.doOnPreDraw {
            entrancePreDraw = null
            if (entrancePending && !isFinishing && !isDestroyed &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                entrancePending = false
                playEntranceAnimation()
            }
        }
    }

    private fun playEntranceAnimation() = with(binding) {
        val bounds = Rect()
        bounds.set(0, 0, contrainPlay1.width, contrainPlay1.height)
        root.offsetDescendantRectToMyCoords(contrainPlay1, bounds)
        val leftOffset = -bounds.right.toFloat()
        contrainPlay1.translationX = leftOffset
        bounds.set(0, 0, contrainPlay2.width, contrainPlay2.height)
        root.offsetDescendantRectToMyCoords(contrainPlay2, bounds)
        val rightOffset = (root.width - bounds.left).toFloat()
        contrainPlay2.translationX = rightOffset

        // Scale in place around the measured centre; 1f is the XML size.
        txtVs.pivotX = txtVs.width / 2f
        txtVs.pivotY = txtVs.height / 2f
        viewflash.pivotX = viewflash.width / 2f
        viewflash.pivotY = viewflash.height / 2f
        txtVs.scaleX = 0f
        txtVs.scaleY = 0f
        contrainPlay1.alpha = 1f
        contrainPlay2.alpha = 1f
        txtVs.alpha = 1f

        val entrance = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(contrainPlay1, View.TRANSLATION_X, leftOffset, 0f)
                    .setDuration(SLIDE_DURATION_MS),
                ObjectAnimator.ofFloat(contrainPlay2, View.TRANSLATION_X, rightOffset, 0f)
                    .setDuration(SLIDE_DURATION_MS),
                scaleAnimator(txtVs, 0f, 1f).apply { startDelay = VS_DELAY_MS },
            )
            interpolator = DecelerateInterpolator()
        }
        val exit = AnimatorSet().apply {
            // The hold starts only after both cards and VS have settled.
            startDelay = HOLD_DURATION_MS
            playTogether(
                ObjectAnimator.ofFloat(contrainPlay1, View.TRANSLATION_X, 0f, leftOffset)
                    .setDuration(SLIDE_DURATION_MS),
                ObjectAnimator.ofFloat(contrainPlay2, View.TRANSLATION_X, 0f, rightOffset)
                    .setDuration(SLIDE_DURATION_MS),
                scaleAnimator(txtVs, 1f, 0f),
                scaleAnimator(viewflash, 1f, 0f),
            )
            interpolator = AccelerateInterpolator()
        }
        val sequence = AnimatorSet().apply {
            playSequentially(entrance, exit)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    playAnimation = null
                    showExitedState()
                }
            })
        }
        playAnimation = sequence
        sequence.start()
    }

    private fun scaleAnimator(view: View, from: Float, to: Float): ObjectAnimator =
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, from, to),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, from, to),
        ).setDuration(VS_DURATION_MS)

    private fun showExitedState() {
        binding.contrainFlash.visibility = View.INVISIBLE
        binding.viewflash.visibility = View.INVISIBLE
        viewModel.onIntroFinished()
        binding.root.post { requestMicrophoneAfterIntro() }
    }

    override fun onScreenStart() {
        viewModel.onScreenStarted(hasMicrophonePermission())
        playAnimation?.resume()
        scheduleEntrance()
        binding.root.post { requestMicrophoneAfterIntro() }
    }

    override fun onScreenStop() {
        barkEffects.clear()
        viewModel.onScreenStopped(isFinishing)
        binding.dualWaveform.stop()
        entrancePreDraw?.removeListener()
        entrancePreDraw = null
        playAnimation?.pause()
    }

    override fun onScreenDestroyed() {
        binding.dualWaveform.setOnPointScoredListener(null)
        barkEffects.dispose()
        rewardedAd?.dispose()
        rewardedAd = null
        viewModel.onRewardedAdBusyChanged(false)
        resultErrorSnackbar?.dismiss()
        resultErrorSnackbar = null
        binding.dualWaveform.stop()
        binding.dualWaveform.setOnBattleFinishedListener(null)
        binding.dualWaveform.setOnBattleProgressChangedListener(null)
        voiceTrackDivider?.dispose()
        voiceTrackDivider = null
        battleAvatarGrowth?.dispose()
        battleAvatarGrowth = null
        binding.dualWaveform.stop()
        backgroundTarget?.let { Glide.with(applicationContext).clear(it) }
        backgroundTarget = null
        // BaseActivity invokes this from onDestroy(), when Glide no longer allows
        // retrieving the RequestManager associated with this Activity.
        waveformAvatarTarget?.let { Glide.with(applicationContext).clear(it) }
        waveformAvatarTarget = null
        appearanceGeneration++
        entrancePending = false
        entrancePreDraw?.removeListener()
        entrancePreDraw = null
        playAnimation?.removeAllListeners()
        playAnimation?.cancel()
        playAnimation = null
    }

    companion object {
        const val EXTRA_USE_MICROPHONE = PlayViewModel.EXTRA_USE_MICROPHONE
        const val SLIDE_DURATION_MS = 500L
        const val VS_DURATION_MS = 280L
        const val VS_DELAY_MS = SLIDE_DURATION_MS - VS_DURATION_MS
        const val HOLD_DURATION_MS = 2_000L
    }
}
