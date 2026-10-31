package com.oc.catemoji.catoc.ui.main.information

import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.toHome
import com.oc.catemoji.catoc.databinding.ActivityInformationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InformationActivity : BaseActivity<ActivityInformationBinding, InformationViewModel>(
    ActivityInformationBinding::inflate,
    InformationViewModel::class.java,
) {
    override fun setupPreViews() {
        enableEdgeToEdge()
    }

    override fun initView() {
        binding.txtTitle.isSelected =true
        binding.txtContinue.isSelected =true
        if (sharedPreferences.isInformationScreen()) {
            toHome()
            return
        }
        val bottomPadding = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding + systemBars.bottom)
            insets
        }
    }

    override fun viewListener() {
        binding.btnContinue.onClick {
            val name = binding.editName.text.toString().trim()
            if (name.isEmpty()) {
                binding.editName.error = getString(R.string.profile_name_required)
                binding.editName.requestFocus()
            } else {
                viewModel.saveProfile(name)
            }
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collect { state ->
                    binding.btnContinue.isEnabled = !state.saving && !state.saved
                    binding.editName.isEnabled = !state.saving && !state.saved
                    if (state.failed) showSnackbar(getString(R.string.profile_save_failed))
                    if (state.saved) toHome()
                }
            }
        }
    }
}
