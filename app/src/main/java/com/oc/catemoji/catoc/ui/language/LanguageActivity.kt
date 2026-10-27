package com.oc.catemoji.catoc.ui.language

import android.content.res.Configuration
import android.os.Bundle
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.toHomeAfterLanguageChange
import com.oc.catemoji.catoc.core.extention.toIntroFromLanguage
import com.oc.catemoji.catoc.databinding.ActivityLanguageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class LanguageActivity : BaseActivity<ActivityLanguageBinding, LanguageViewModel>(
    ActivityLanguageBinding::inflate,
    LanguageViewModel::class.java
) {
    private var applyingLanguage = false
    private var pendingDestination = DESTINATION_NONE
    private var navigationPosted = false
    private val fromSetting: Boolean
        get() = intent.getBooleanExtra(EXTRA_FROM_SETTING, false)
    private val languageAdapter by lazy {
        LanguageAdapter(this).apply {
            onItemClick = { code ->
                if (!applyingLanguage) viewModel.selectLanguage(code)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        pendingDestination = savedInstanceState?.getInt(KEY_PENDING_DESTINATION) ?: DESTINATION_NONE
        applyingLanguage = pendingDestination != DESTINATION_NONE
        super.onCreate(savedInstanceState)
        binding.actionBar.btnActionBarRight.isEnabled = !applyingLanguage
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_PENDING_DESTINATION, pendingDestination)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateActionBar(viewModel.isFirstLanguage.value, viewModel.codeLang.value)
        navigateWhenLocaleApplied()
    }

    override fun onResume() {
        super.onResume()
        navigateWhenLocaleApplied()
    }

    override fun setupPreViews() {
        binding.recycleLanguage.adapter = languageAdapter
        binding.recycleLanguage.itemAnimator = null
        viewModel.setFirstLanguage(!fromSetting && !sharedPreferences.isLanuageScreen())
        viewModel.loadLanguages(sharedPreferences.isLanguageKey())
        languageAdapter.submitList(viewModel.languageList.value)
    }

    override fun initView() {
        binding.actionBar.btnActionBarLeft.setImageResource(R.drawable.back_app)
        binding.actionBar.btnActionBarRight.setImageResource(R.drawable.select_language)
        updateActionBar(viewModel.isFirstLanguage.value, viewModel.codeLang.value)

    }

    private fun updateActionBar(isFirst: Boolean, code: String) {
        binding.actionBar.apply {
            tvStart.isVisible = isFirst
            tvCenter.isVisible = !isFirst
            setTextActionBar(if (isFirst) tvStart else tvCenter, getString(R.string.language))
            // Keep the header's vertical anchor when onboarding hides the back button.
            btnActionBarLeft.isInvisible = isFirst
            btnActionBarRight.isVisible = code.isNotEmpty()
            btnActionBarRight.isEnabled = !applyingLanguage && code.isNotEmpty()
        }
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarRight.onClick { handleDone() }
        binding.actionBar.btnActionBarLeft.onClick { onScreenBackPressed() }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.languageList.collect(languageAdapter::submitList) }
                launch {
                    combine(viewModel.isFirstLanguage, viewModel.codeLang) { first, code ->
                        first to code
                    }.collect { (first, code) -> updateActionBar(first, code) }
                }
            }
        }
    }

    override fun onScreenBackPressed(): Boolean {
        if (!applyingLanguage) {
            if (fromSetting || sharedPreferences.isLanuageScreen()) finish() else finishAffinity()
        }
        return true
    }

    override fun bindViewModel() = Unit

    private fun handleDone() {
        if (applyingLanguage) return
        val code = viewModel.codeLang.value
        if (code.isEmpty()) {
            showToast(R.string.not_select_lang)
            return
        }

        applyingLanguage = true
        binding.actionBar.btnActionBarRight.isEnabled = false
        pendingDestination = if (viewModel.isFirstLanguage.value) {
            DESTINATION_INTRO
        } else {
            DESTINATION_HOME
        }
        sharedPreferences.setLanuageScreen(true)
        if (updateAppLanguage(code)) {
            navigateWhenLocaleApplied()
        } else {
            // pendingDestination được lưu; Activity mới sẽ nhận locale trong attachBaseContext().
            recreate()
        }
    }

    private fun navigateWhenLocaleApplied() {
        if (pendingDestination == DESTINATION_NONE || navigationPosted || isFinishing || isDestroyed) return
        // Android canonicalizes legacy codes such as Indonesian "in" to "id".
        val appliedLanguage = resources.configuration.locales[0].toLanguageTag()
        val selectedLanguage = Locale.forLanguageTag(viewModel.codeLang.value).toLanguageTag()
        if (!appliedLanguage.equals(selectedLanguage, ignoreCase = true)) return

        navigationPosted = true
        binding.root.post {
            navigationPosted = false
            if (isFinishing || isDestroyed || pendingDestination == DESTINATION_NONE) return@post
            val destination = pendingDestination
            pendingDestination = DESTINATION_NONE
            when (destination) {
                DESTINATION_INTRO -> toIntroFromLanguage()
                DESTINATION_HOME -> toHomeAfterLanguageChange()
            }
        }
    }

    companion object {
        const val EXTRA_FROM_SETTING = "fromSetting"
        private const val KEY_PENDING_DESTINATION = "pendingLanguageDestination"
        private const val DESTINATION_NONE = 0
        private const val DESTINATION_INTRO = 1
        private const val DESTINATION_HOME = 2
    }
}
