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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.toInformation
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
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
    private var entrancePreDraw: OneShotPreDrawListener? = null
    private var entrancePending = false
    private var renderedAppearance: Pair<String, String>? = null
    private var appearanceGeneration = 0
    private var imagesReady = false
    private var playAnimation: AnimatorSet? = null
    private var introFinished = false
    private var microphonePermissionPending = false
    private var microphonePermissionBlocked = false
    private var microphoneAutoRequested = false
    private var waveformAvatarTarget: CustomTarget<Drawable>? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphonePermissionPending = false
        if (granted) {
            microphonePermissionBlocked = false
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && introFinished) viewModel.startListening()
        } else {
            microphonePermissionBlocked = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            viewModel.requireMicrophonePermission()
        }
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.namePlay1.isSelected = true
        binding.txtCoinRank1.isSelected = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        introFinished = savedInstanceState?.getBoolean(INTRO_FINISHED) == true ||
            binding.contrainFlash.visibility == View.GONE || binding.viewflash.visibility == View.GONE
        microphonePermissionBlocked = savedInstanceState?.getBoolean(MICROPHONE_BLOCKED) == true
        microphoneAutoRequested = savedInstanceState?.getBoolean(MICROPHONE_AUTO_REQUESTED) == true
        entrancePending = !introFinished
        binding.progressPlay.isVisible = true
        if (entrancePending) {
            binding.contrainPlay1.alpha = 0f
            binding.contrainPlay2.alpha = 0f
            binding.txtVs.alpha = 0f
        } else {
            showExitedState()
        }
    }

    override fun viewListener() {
        binding.btnMicrophone.onClick {
            when (viewModel.microphone.value.status) {
                MicrophoneStatus.STARTING, MicrophoneStatus.LISTENING -> viewModel.stopListening()
                else -> when {
                    hasMicrophonePermission() -> viewModel.startListening()
                    microphonePermissionBlocked -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
                    )
                    !microphonePermissionPending -> {
                        microphonePermissionPending = true
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.microphone.collect(::renderMicrophone) }
                launch {
                    viewModel.barkCount.collect { count ->
                        binding.txtBarkCount.text = getString(R.string.play_bark_count, count)
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
        if (!introFinished || microphoneAutoRequested || isFinishing || isDestroyed ||
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
        val active = state.status == MicrophoneStatus.STARTING || state.status == MicrophoneStatus.LISTENING
        binding.btnMicrophone.setText(when {
            active -> R.string.play_microphone_stop
            microphonePermissionBlocked && !hasMicrophonePermission() -> R.string.play_microphone_settings
            else -> R.string.play_microphone_start
        })
        val detection = state.detection
        val status = state.error ?: when (state.status) {
            MicrophoneStatus.IDLE -> R.string.play_microphone_ready
            MicrophoneStatus.STARTING -> R.string.play_microphone_starting
            MicrophoneStatus.LISTENING -> when {
                detection?.isBark == true -> R.string.play_bark_detected
                detection?.top != null && detection.level >= 0.005f -> R.string.play_bark_not_detected
                else -> R.string.play_microphone_listening
            }
            MicrophoneStatus.PERMISSION_REQUIRED -> R.string.play_microphone_permission
            MicrophoneStatus.ERROR -> R.string.play_microphone_error
        }
        binding.txtMicrophoneStatus.setText(status)
        if (state.status == MicrophoneStatus.STARTING) binding.dualWaveform.clear()
        if (active) binding.dualWaveform.start() else binding.dualWaveform.stop()
        binding.dualWaveform.addRealAmplitude(((detection?.level ?: 0f) * 32767).roundToInt())
        binding.txtSoundClass.text = detection?.top?.let { top ->
            getString(R.string.play_sound_result, top.label, (top.score * 100).roundToInt())
        }.orEmpty()
    }

    private fun renderPlayer(user: UserEntity) {
        binding.namePlay1.text = user.name
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
        var remainingImages = 2
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
        loadAppearance(binding.imgAvatar1, user.imageAvatar, R.drawable.image_avatar_infor, onImageReady)
        loadAppearance(binding.imgFrameAvatar1, user.imageFrame, R.drawable.img_frame_avatar, onImageReady)
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

    private fun showExitedState() {
        binding.contrainFlash.visibility = View.INVISIBLE
        binding.viewflash.visibility = View.INVISIBLE
        binding.microphonePanel.isVisible = true
        binding.root.post { requestMicrophoneAfterIntro() }
    }

    override fun onScreenStart() {
        if (hasMicrophonePermission() && viewModel.microphone.value.status == MicrophoneStatus.PERMISSION_REQUIRED) {
            microphonePermissionBlocked = false
            viewModel.stopListening()
        }
        playAnimation?.resume()
        scheduleEntrance()
        binding.root.post { requestMicrophoneAfterIntro() }
    }

    override fun onScreenStop() {
        binding.dualWaveform.stop()
        viewModel.stopListening()
        entrancePreDraw?.removeListener()
        entrancePreDraw = null
        playAnimation?.pause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(INTRO_FINISHED, introFinished)
        outState.putBoolean(MICROPHONE_BLOCKED, microphonePermissionBlocked)
        outState.putBoolean(MICROPHONE_AUTO_REQUESTED, microphoneAutoRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onScreenDestroyed() {
        binding.dualWaveform.stop()
        waveformAvatarTarget?.let { Glide.with(this).clear(it) }
        waveformAvatarTarget = null
        appearanceGeneration++
        entrancePending = false
        entrancePreDraw?.removeListener()
        entrancePreDraw = null
        playAnimation?.removeAllListeners()
        playAnimation?.cancel()
        playAnimation = null
    }

    private companion object {
        const val SLIDE_DURATION_MS = 500L
        const val VS_DURATION_MS = 280L
        const val VS_DELAY_MS = SLIDE_DURATION_MS - VS_DURATION_MS
        const val HOLD_DURATION_MS = 2_000L
        const val INTRO_FINISHED = "play_intro_finished"
        const val MICROPHONE_BLOCKED = "play_microphone_blocked"
        const val MICROPHONE_AUTO_REQUESTED = "play_microphone_auto_requested"
    }
}
