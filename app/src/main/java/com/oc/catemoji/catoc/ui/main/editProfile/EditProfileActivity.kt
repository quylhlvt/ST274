package com.oc.catemoji.catoc.ui.main.editProfile

import android.app.Activity
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.hideSoftKeyboard
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.databinding.ActivityEditProfileBinding
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditProfileActivity : BaseActivity<ActivityEditProfileBinding, EditProfileViewModel>(
    ActivityEditProfileBinding::inflate, EditProfileViewModel::class.java,
) {
    override val retainScreenViewState = false
    private val avatarAdapter = ProfileAvatarAdapter { viewModel.selectAvatar(it) }
    private val frameAdapter = ProfileFrameAdapter { viewModel.selectFrame(it) }
    private var renderedAvatar: String? = null
    private var renderedFrame: String? = null
    private var revealedSelection = false

    override fun initView() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.edit_profile))
        }
        binding.recycleAvatar.apply {
            layoutManager = LinearLayoutManager(this@EditProfileActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = avatarAdapter
        }
        binding.recycleBorderFrame.apply {
            layoutManager = LinearLayoutManager(this@EditProfileActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = frameAdapter
        }
        binding.editName.isEnabled = false
        binding.btnSave.isEnabled = false
        binding.imgAvatar.setImageDrawable(shimmer)
        binding.imgFrameAvatar.setImageDrawable(null)
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { onBackPressedDispatcher.onBackPressed() }
        binding.editName.doAfterTextChanged { viewModel.setName(it.toString()) }
        binding.editName.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                hideSoftKeyboard()
                binding.editName.clearFocus()
                true
            } else false
        }
        binding.btnSave.onClick { viewModel.save() }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val editable = state.ready && !state.saving && !state.saved
                    binding.editName.isEnabled = editable
                    binding.btnSave.isEnabled = editable
                    binding.btnSave.alpha = if (editable) 1f else 0.5f
                    binding.progressProfile.isVisible = state.loading || state.saving
                    avatarAdapter.render(state.avatars, state.avatar, editable, state.unlockedAvatars)
                    frameAdapter.render(state.frames, state.frame, editable, state.unlockedFrames)
                    if (state.ready && !revealedSelection) {
                        revealedSelection = true
                        binding.recycleAvatar.scrollToPosition(state.avatars.indexOf(state.avatar))
                        binding.recycleBorderFrame.scrollToPosition(state.frames.indexOf(state.frame))
                    }
                    if (binding.editName.text.toString() != state.name) {
                        binding.editName.setText(state.name)
                        binding.editName.setSelection(state.name.length)
                    }
                    binding.editName.error = if (state.nameRequired) getString(R.string.profile_name_required) else null
                    if (state.nameRequired) binding.editName.requestFocus()
                    if (state.ready && renderedAvatar != state.avatar) {
                        renderedAvatar = state.avatar
                        Glide.with(this@EditProfileActivity).clear(binding.imgAvatar)
                        loadImage(this@EditProfileActivity, state.avatar, binding.imgAvatar)
                    }
                    if (state.ready && renderedFrame != state.frame) {
                        renderedFrame = state.frame
                        Glide.with(this@EditProfileActivity).clear(binding.imgFrameAvatar)
                        binding.imgFrameAvatar.setImageDrawable(null)
                        Glide.with(this@EditProfileActivity).load(state.frame).dontAnimate()
                            .into(binding.imgFrameAvatar)
                    }
                    state.error?.let {
                        showSnackbar(getString(it))
                        viewModel.clearError()
                    }
                    if (state.saved && !isFinishing) {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }
    companion object {
        private val shimmer  = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

    }
    override fun onScreenBackPressed(): Boolean = viewModel.state.value.saving
}
