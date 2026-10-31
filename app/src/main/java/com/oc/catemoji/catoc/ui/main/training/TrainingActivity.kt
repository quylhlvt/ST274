package com.oc.catemoji.catoc.ui.main.training

import android.graphics.drawable.Drawable
import android.view.View
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.oc.catemoji.catoc.core.helper.BarkEffectRenderer
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.databinding.ActivityTrainingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrainingActivity : BaseActivity<ActivityTrainingBinding, TrainingViewModel>(
    ActivityTrainingBinding::inflate, TrainingViewModel::class.java,
) {
    override val retainScreenViewState = false
    private lateinit var barkEffects: BarkEffectRenderer
    private var backgroundPath: String? = null
    private var backgroundTarget: CustomViewTarget<View, Drawable>? = null
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
                viewModel.startListening()
            }
        } else {
            showToast(R.string.play_microphone_permission)
        }
    }

    override fun initView() {
        barkEffects = BarkEffectRenderer(binding.root, binding.effectLayer)
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.training))
            btnActionBarLeft.contentDescription = getString(R.string.navigate_back)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // layout_actionbar already reserves the top space, as on Play/Profile.
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
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
        binding.actionBar.btnActionBarLeft.onClick {
            onBackPressedDispatcher.onBackPressed()
        }
        // Each training tap can produce an effect, including rapid consecutive taps.
        binding.imgAvatar.setOnClickListener { if (!useMicrophone) viewModel.addPlayerTap() }
        binding.imgAvatar.isClickable = !useMicrophone
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
                launch { viewModel.effects.collect(barkEffects::setEffects) }
                launch {
                    viewModel.barkCount.collect { target ->
                        var count = viewModel.consumedBarks
                        binding.txtBarkCount.text = getString(R.string.play_bark_count, count)
                        while (count < target) {
                            count++
                            viewModel.consumedBarks = count
                            binding.txtBarkCount.text = getString(R.string.play_bark_count, count)
                            barkEffects.show(binding.imgAvatar)
                        }
                    }
                }
                launch {
                    viewModel.microphoneError.collect { error ->
                        if (error != null) showSnackbar(getString(error))
                    }
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
            viewModel.startListening()
        } else {
            microphonePermissionPending = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
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

    override fun onScreenStop() {
        barkEffects.clear()
        viewModel.stopListening()
    }

    override fun onScreenDestroyed() {
        viewModel.stopListening()
        barkEffects.dispose()
        backgroundTarget?.let { Glide.with(applicationContext).clear(it) }
        backgroundTarget = null
    }

    companion object {
        const val EXTRA_USE_MICROPHONE = "training_use_microphone"
    }
}
