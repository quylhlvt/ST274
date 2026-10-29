package com.oc.catemoji.catoc.ui.main.training

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.Manifest
import android.content.pm.PackageManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.databinding.ActivityTrainingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.oc.catemoji.catoc.data.audio.DeviceSoundClassifier

@AndroidEntryPoint
class TrainingActivity : BaseActivity<ActivityTrainingBinding, TrainingViewModel>(
    ActivityTrainingBinding::inflate, TrainingViewModel::class.java,
) {
    override val retainScreenViewState = false
    private val activeEffects = mutableMapOf<ImageView, AnimatorSet?>()
    private var backgroundPath: String? = null
    private var backgroundTarget: CustomViewTarget<View, Drawable>? = null
    private val soundClassifier by lazy { DeviceSoundClassifier(applicationContext) }
    private var previousBarkCount = 0
    private var lastPredictionAtMs = 0L
    private var microphoneJob: kotlinx.coroutines.Job? = null
    private var microphonePermissionPending = false
    private val useMicrophone get() = intent.getBooleanExtra(EXTRA_USE_MICROPHONE, false)
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphonePermissionPending = false
        if (granted) {
            if (useMicrophone && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                !isFinishing && !isDestroyed
            ) {
                startListening()
            }
        } else {
            showToast(R.string.play_microphone_permission)
        }
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "Training opened, useMicrophone=$useMicrophone")
    }

    override fun onScreenStart() {
        // onScreenStop cancels recording, so resume it on re-entry.
        if (!useMicrophone) return
        binding.root.post {
            if (!isDestroyed && !isFinishing &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                startMicrophoneIfAllowed()
            }
        }
    }

    override fun viewListener() {
        // Each training tap can produce an effect, including rapid consecutive taps.
        binding.imgAvatar.setOnClickListener { showRandomEffect() }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModelActivity.homeState.collect { state ->
                        when (state) {
                            RoomLoadState.Loading -> Unit
                            is RoomLoadState.Error -> showToast(R.string.home_profile_load_failed)
                            is RoomLoadState.Success -> renderDogPreview(state.data.dogPreview)
                        }
                    }
                }
                launch {
                    viewModelActivity.barkEvents.collect { showRandomEffect() }
                }
                launch {
                    binding.txtBarkCount.text = getString(R.string.play_bark_count, previousBarkCount)
                }
                viewModel.background.collect { path ->
                    if (path != null && path != backgroundPath) loadBackground(path)
                }
            }
        }
    }

    private fun startMicrophoneIfAllowed() {
        if (!useMicrophone || microphonePermissionPending ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || isFinishing || isDestroyed
        ) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startListening()
        } else {
            microphonePermissionPending = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (microphoneJob?.isActive == true) return
        lastPredictionAtMs = 0L
        microphoneJob = lifecycleScope.launch {
            val baseCount = previousBarkCount
            Log.d(TAG, "Starting scored microphone session")
            try {
                soundClassifier.listenScored()
                    // Chạy phần upstream (thu âm / suy luận đồng bộ) ngoài UI thread.
                    .flowOn(Dispatchers.Default)
                    .collect { update ->
                        // Ignore cheap waveform-only updates when measuring inference cadence.
                        if (update.prediction.hasClassification) {
                            val now = SystemClock.elapsedRealtime()
                            if (lastPredictionAtMs != 0L) {
                                val gap = now - lastPredictionAtMs
                                if (gap >= SLOW_PREDICTION_GAP_MS) {
                                    Log.w(TAG, "Time between AI predictions = ${gap}ms")
                                }
                            }
                            lastPredictionAtMs = now
                        }
                        val count = baseCount + update.totalPoints

                        if (count > previousBarkCount) {
                            val delta = count - previousBarkCount
                            previousBarkCount = count

                            // Cập nhật điểm trước, không chờ Glide/animation.
                            binding.txtBarkCount.text =
                                getString(R.string.play_bark_count, count)

                            // Các hiệu ứng hình ảnh được lên lịch riêng để
                            // tránh block collector khi tải hình bằng Glide.
                            repeat(delta.coerceAtMost(MAX_ACTIVE_EFFECTS)) {
                                binding.root.post {
                                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                        showRandomEffect()
                                    }
                                }
                            }
                        }
                    }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val root = generateSequence(e as Throwable?) { it.cause }.lastOrNull() ?: e
                val detail = "${root.javaClass.simpleName}: ${root.message ?: "unknown error"}"
                Log.e(TAG, "Sound detection failed: $detail", e)
                if (!isFinishing && !isDestroyed) {
                    // Temporary diagnostic; remove the detailed error after fixing startup.
                    showSnackbar("Audio startup error: ${detail.take(160)}")
                }
            }
        }
    }

    private fun renderDogPreview(path: String?) {
        if (isFinishing || isDestroyed) return
        if (path.isNullOrBlank()) {
            Glide.with(this).clear(binding.imgAvatar)
            binding.imgAvatar.setImageDrawable(null)
            return
        }
        Glide.with(this)
            .load(path)
            .placeholder(R.drawable.image_avatar_infor)
            .error(R.drawable.image_avatar_infor)
            .dontAnimate()
            .into(binding.imgAvatar)
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

    private fun showRandomEffect() {
        if (isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) return
        if (activeEffects.size >= MAX_ACTIVE_EFFECTS) return
        val effect = viewModel.randomEffect() ?: return
        val avatar = binding.imgAvatar
        val size = minOf(avatar.width, avatar.height)
        if (size <= 0) return

        val bounds = Rect(0, 0, avatar.width, avatar.height)
        binding.root.offsetDescendantRectToMyCoords(avatar, bounds)
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        binding.effectLayer.addView(image, FrameLayout.LayoutParams(size, size).apply {
            leftMargin = bounds.centerX() - binding.effectLayer.left - size / 2
            topMargin = bounds.centerY() - binding.effectLayer.top - size / 2
        })
        activeEffects[image] = null
        Glide.with(this).load(effect.path).dontAnimate()
            .into(object : DrawableImageViewTarget(image) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    super.onResourceReady(resource, transition)
                    if (activeEffects.containsKey(image) && !isFinishing && !isDestroyed) {
                        animateEffect(image, effect.folder, size)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    // Clear the request outside Glide's callback.
                    image.post { removeEffect(image) }
                }
            })
    }

    private fun animateEffect(image: ImageView, folder: Int, size: Int) {
        image.alpha = 1f
        val stationary = folder == 1
        val growDuration = if (stationary) BURST_DURATION_MS else FLIGHT_DURATION_MS
        val endScale = if (stationary) 1.8f else 1.4f
        val distance = size * 1.6f
        val animation = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(image, View.SCALE_X, 0.15f, endScale).apply {
                    duration = growDuration
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(image, View.SCALE_Y, 0.15f, endScale).apply {
                    duration = growDuration
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(image, View.TRANSLATION_X, 0f, if (folder == 2) distance else 0f)
                    .setDuration(growDuration),
                ObjectAnimator.ofFloat(
                    image,
                    View.TRANSLATION_Y,
                    0f,
                    when (folder) {
                        2 -> -distance
                        3 -> -distance
                        else -> 0f
                    },
                )
                    .setDuration(growDuration),
                ObjectAnimator.ofFloat(image, View.ALPHA, 1f, 0f).apply {
                    startDelay = BURST_DURATION_MS
                    duration = FADE_DURATION_MS
                },
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // With system animations disabled this can run inside Glide's callback.
                    image.post { removeEffect(image) }
                }
            })
        }
        activeEffects[image] = animation
        animation.start()
    }

    private fun removeEffect(image: ImageView) {
        activeEffects.remove(image)?.apply {
            removeAllListeners()
            cancel()
        }
        // App-scoped RequestManager is safe even when a delayed callback executes
        // after Activity destruction. Glide.with(this) is NOT safe in that case.
        Glide.with(applicationContext).clear(image)
        binding.effectLayer.removeView(image)
    }

    override fun onScreenStop() {
        activeEffects.keys.toList().forEach(::removeEffect)
        microphoneJob?.cancel()
        microphoneJob = null
        lastPredictionAtMs = 0L
    }

    override fun onScreenDestroyed() {
        microphoneJob?.cancel()
        microphoneJob = null
        activeEffects.keys.toList().forEach(::removeEffect)
        // BaseActivity invokes this during onDestroy; the Activity RequestManager
        // cannot be obtained here because Glide rejects a destroyed Activity.
        backgroundTarget?.let { Glide.with(applicationContext).clear(it) }
        backgroundTarget = null
    }

    companion object {
        private const val TAG = "TrainingAudio"
        private const val SLOW_PREDICTION_GAP_MS = 700L
        const val EXTRA_USE_MICROPHONE = "training_use_microphone"

        private const val MAX_ACTIVE_EFFECTS = 12
        private const val BURST_DURATION_MS = 180L
        private const val FADE_DURATION_MS = 380L
        private const val FLIGHT_DURATION_MS = BURST_DURATION_MS + FADE_DURATION_MS
    }
}