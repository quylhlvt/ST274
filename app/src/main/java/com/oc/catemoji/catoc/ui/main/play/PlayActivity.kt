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
import com.oc.catemoji.catoc.core.custom.VoiceTrackDivider
import com.oc.catemoji.catoc.core.custom.BattleAvatarGrowth
import com.oc.catemoji.catoc.core.extention.toInformation
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.databinding.ActivityPlayBinding
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class PlayActivity : BaseActivity<ActivityPlayBinding, PlayViewModel>(
    ActivityPlayBinding::inflate, PlayViewModel::class.java,
) {
    override val retainScreenViewState = false
    private val useMicrophone get() = intent.getBooleanExtra(EXTRA_USE_MICROPHONE, false)
    private var lastMicrophoneStatus: MicrophoneStatus? = null
    private var entrancePreDraw: OneShotPreDrawListener? = null
    private var entrancePending = false
    private var renderedAppearance: Pair<String, String>? = null
    private var appearanceGeneration = 0
    private var imagesReady = false
    private var playerReady = false
    private var playAnimation: AnimatorSet? = null
    private var introFinished = false
    private var battleStarted = false
    private var countdownStep = 0
    private var countdownJob: Job? = null
    private var microphonePermissionPending = false
    private var microphonePermissionBlocked = false
    private var microphoneAutoRequested = false
    private var restartMicrophoneOnStart = false
    private var waveformAvatarTarget: CustomTarget<Drawable>? = null
    private var backgroundPath: String? = null
    private var backgroundTarget: CustomViewTarget<View, Drawable>? = null
    private var voiceTrackDivider: VoiceTrackDivider? = null
    private var battleAvatarGrowth: BattleAvatarGrowth? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphonePermissionPending = false
        if (granted) {
            microphonePermissionBlocked = false
            if (useMicrophone && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                battleStarted && !binding.dualWaveform.isBattleFinished) viewModel.startListening()
        } else {
            microphonePermissionBlocked = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            viewModel.requireMicrophonePermission()
        }
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
        viewModel.setMicrophoneEnabled(false)
        viewModel.selectDetectionMode(BarkDetectionMode.WORDS)
        binding.namePlay1.isSelected = true
        binding.txtCoinRank1.isSelected = true
        binding.txtNameAvatar1.isSelected = true
        binding.txtNameAvatar2.apply {
            isSelected = true
            setText(R.string.play_opponent_name)
        }
        binding.namePlay2.setText(R.string.play_opponent_name)
        binding.avatar2.contentDescription = getString(R.string.play_opponent_name)
        binding.avatar1.isEnabled = false
        binding.avatar2.isEnabled = false
        binding.dualWaveform.setOnBattleFinishedListener {
            viewModel.stopListening()
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
            divider.updateBattleProgress(progress)
            growth.updateBattleProgress(progress)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        introFinished = savedInstanceState?.getBoolean(INTRO_FINISHED) == true
        battleStarted = savedInstanceState?.getBoolean(BATTLE_STARTED) == true
        countdownStep = savedInstanceState?.getInt(COUNTDOWN_STEP, 0)?.coerceIn(0, 4) ?: 0
        binding.txtStartNumber.visibility = View.INVISIBLE
        if (battleStarted) enableBattleControls()
        microphonePermissionBlocked = savedInstanceState?.getBoolean(MICROPHONE_BLOCKED) == true
        microphoneAutoRequested = savedInstanceState?.getBoolean(MICROPHONE_AUTO_REQUESTED) == true
        restartMicrophoneOnStart = savedInstanceState?.getBoolean(MICROPHONE_WAS_ACTIVE) == true
        entrancePending = !introFinished
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
        // Every tap counts, including rapid alternating taps on the two sides.
        binding.avatar1.setOnClickListener {
            if (!useMicrophone && battleStarted && !binding.dualWaveform.isBattleFinished) viewModel.addPlayerTap()
        }
        binding.avatar2.setOnClickListener {
            if (!useMicrophone && battleStarted && !binding.dualWaveform.isBattleFinished) binding.dualWaveform.addOpponentPoint()
        }
        binding.avatar1.isClickable = !useMicrophone
        binding.avatar2.isClickable = !useMicrophone
        binding.actionBar.btnActionBarLeft.onClick {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.microphone.collect(::renderMicrophone) }
                launch {
                    viewModel.background.collect { path ->
                        if (path != null && path != backgroundPath) loadBackground(path)
                    }
                }
                launch {
                    var displayedCount: Int? = null
                    viewModel.barkCount.collect { targetCount ->
                        val current = displayedCount
                        if (current == null || targetCount <= current) {
                            displayedCount = targetCount
                            binding.txtBarkCount.text = getString(R.string.play_bark_count, targetCount)
                            return@collect
                        }

                        // One inference window may confirm several queued barks.
                        // Present every point separately so battle movement can
                        // consume exactly one left/right push per bark event.
                        var nextCount = current
                        while (nextCount < targetCount) {
                            if (binding.dualWaveform.isBattleFinished) break
                            nextCount++
                            displayedCount = nextCount
                            binding.txtBarkCount.text = getString(R.string.play_bark_count, nextCount)
                            binding.dualWaveform.addPlayerPoint()
                            viewModelActivity.notifyBarkDetected()
                            // Keep every number visible, including the boundary
                            // between two consecutive recognition updates.
                            delay(BARK_COUNT_STEP_DELAY_MS)
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

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicrophoneAfterIntro() {
        if (!useMicrophone || !battleStarted || microphoneAutoRequested || binding.dualWaveform.isBattleFinished || isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        microphoneAutoRequested = true
        when {
            hasMicrophonePermission() -> viewModel.startListening()
            microphonePermissionBlocked -> viewModel.requireMicrophonePermission()
            !microphonePermissionPending -> {
                microphonePermissionPending = true
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun renderMicrophone(state: MicrophoneUiState) {
        if (!useMicrophone) return
        val active = state.status == MicrophoneStatus.STARTING || state.status == MicrophoneStatus.LISTENING
        if (active && battleStarted) binding.dualWaveform.start() else binding.dualWaveform.stop()
        binding.dualWaveform.addRealAmplitude(((state.detection?.level ?: 0f) * 32767).roundToInt())
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
                    microphoneAutoRequested = false
                    requestMicrophoneAfterIntro()
                }.show()
                else -> Unit
            }
        }
        lastMicrophoneStatus = state.status
    }

    private fun renderPlayer(user: UserEntity) {
        playerReady = true
        if (!useMicrophone) {
            binding.dualWaveform.setTapOpponentInterval(TapBattleDifficulty.opponentIntervalMs(user.rank))
            startTapBattleIfReady()
        }
        startCountdownIfReady()
        binding.namePlay1.text = user.name
        binding.txtNameAvatar1.text = user.name
        binding.avatar1.contentDescription = user.name
        binding.txtCoinRank1.text = user.rankbatter.toString()
        val rankIndex = (user.rank.coerceAtLeast(1) - 1).coerceAtMost(DataLocal.rankList.lastIndex)
        binding.imgRank1.setImageResource(DataLocal.rankList.getOrNull(rankIndex) ?: R.drawable.img_rank)
        binding.imgRank1.contentDescription = getString(R.string.home_rank_description, user.rank)
        binding.frameAvatar1.contentDescription = getString(R.string.home_name_description, user.name)

        val appearance = user.imageAvatar to user.imageFrame
        if (renderedAppearance == appearance) {
            if (imagesReady) {
                binding.progressPlay.isVisible = false
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
                    binding.progressPlay.isVisible = false
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
                    introFinished = true
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

    private fun startTapBattleIfReady() {
        if (!useMicrophone && playerReady && battleStarted && !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            binding.dualWaveform.showStaticWaveform()
        }
    }

    private fun showExitedState() {
        binding.contrainFlash.visibility = View.INVISIBLE
        binding.viewflash.visibility = View.INVISIBLE
        startCountdownIfReady()
        startTapBattleIfReady()
        binding.root.post { requestMicrophoneAfterIntro() }
    }

    private fun startCountdownIfReady() {
        if (!introFinished || !playerReady || battleStarted || countdownJob?.isActive == true ||
            isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        countdownJob = lifecycleScope.launch {
            binding.txtStartNumber.visibility = View.VISIBLE
            while (countdownStep < 4) {
                binding.txtStartNumber.text = if (countdownStep < 3) {
                    getString(R.string.play_countdown_number, 3 - countdownStep)
                } else {
                    getString(R.string.play_countdown_start)
                }
                delay(if (countdownStep < 3) 1_000L else 600L)
                countdownStep++
            }
            binding.txtStartNumber.visibility = View.INVISIBLE
            battleStarted = true
            enableBattleControls()
            startTapBattleIfReady()
            requestMicrophoneAfterIntro()
        }
    }

    private fun enableBattleControls() {
        val canPlay = !binding.dualWaveform.isBattleFinished
        binding.avatar1.isEnabled = canPlay && !useMicrophone
        binding.avatar2.isEnabled = canPlay && !useMicrophone
        viewModel.setMicrophoneEnabled(canPlay && useMicrophone)
    }

    override fun onScreenStart() {
        val permissionGranted = useMicrophone && hasMicrophonePermission()
        val permissionRecovered = permissionGranted &&
            (microphonePermissionBlocked || viewModel.microphone.value.status == MicrophoneStatus.PERMISSION_REQUIRED)
        if (permissionRecovered) {
            microphonePermissionBlocked = false
            viewModel.stopListening()
        }
        val shouldRestart = restartMicrophoneOnStart || permissionRecovered
        restartMicrophoneOnStart = false
        if (useMicrophone && shouldRestart && battleStarted && !binding.dualWaveform.isBattleFinished) {
            if (permissionGranted) viewModel.startListening() else viewModel.requireMicrophonePermission()
        }
        startTapBattleIfReady()
        playAnimation?.resume()
        scheduleEntrance()
        startCountdownIfReady()
        binding.root.post { requestMicrophoneAfterIntro() }
    }

    override fun onScreenStop() {
        countdownJob?.cancel()
        countdownJob = null
        val microphoneStatus = viewModel.microphone.value.status
        restartMicrophoneOnStart = !isFinishing &&
            (microphoneStatus == MicrophoneStatus.STARTING || microphoneStatus == MicrophoneStatus.LISTENING)
        binding.dualWaveform.stop()
        viewModel.stopListening()
        entrancePreDraw?.removeListener()
        entrancePreDraw = null
        playAnimation?.pause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(INTRO_FINISHED, introFinished)
        outState.putBoolean(BATTLE_STARTED, battleStarted)
        outState.putInt(COUNTDOWN_STEP, countdownStep)
        outState.putBoolean(MICROPHONE_BLOCKED, microphonePermissionBlocked)
        outState.putBoolean(MICROPHONE_AUTO_REQUESTED, microphoneAutoRequested)
        val microphoneStatus = viewModel.microphone.value.status
        outState.putBoolean(
            MICROPHONE_WAS_ACTIVE,
            restartMicrophoneOnStart || microphoneStatus == MicrophoneStatus.STARTING ||
                microphoneStatus == MicrophoneStatus.LISTENING,
        )
        super.onSaveInstanceState(outState)
    }

    override fun onScreenDestroyed() {
        countdownJob?.cancel()
        countdownJob = null
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
        const val EXTRA_USE_MICROPHONE = "play_use_microphone"
        const val BARK_COUNT_STEP_DELAY_MS = 80L
        const val SLIDE_DURATION_MS = 500L
        const val VS_DURATION_MS = 280L
        const val VS_DELAY_MS = SLIDE_DURATION_MS - VS_DURATION_MS
        const val HOLD_DURATION_MS = 2_000L
        const val INTRO_FINISHED = "play_intro_finished"
        const val BATTLE_STARTED = "play_battle_started"
        const val COUNTDOWN_STEP = "play_countdown_step"
        const val MICROPHONE_BLOCKED = "play_microphone_blocked"
        const val MICROPHONE_AUTO_REQUESTED = "play_microphone_auto_requested"
        const val MICROPHONE_WAS_ACTIVE = "play_microphone_was_active"
    }
}
