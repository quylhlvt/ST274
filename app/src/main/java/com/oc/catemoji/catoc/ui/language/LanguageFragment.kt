package com.oc.catemoji.catoc.ui.language

import android.content.res.Configuration
import androidx.fragment.app.viewModels
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.popBack
import com.oc.catemoji.catoc.core.extention.toHomeFromLanguage
import com.oc.catemoji.catoc.core.extention.toIntroFromLanguage
import com.oc.catemoji.catoc.core.extention.toSettingFromLang
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.LanguageHelper.setLocale
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager.isLanguageKey
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager.isLanuageScreen
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager.sharedPreferences
import com.oc.catemoji.catoc.databinding.FragmentLanguageBinding
import com.oc.catemoji.catoc.utils.DataLocal
import com.oc.catemoji.catoc.utils.LanguageManager.updateLanguage
import com.oc.catemoji.catoc.utils.key.IntentKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@AndroidEntryPoint
class LanguageFragment : BaseFragment<FragmentLanguageBinding, LanguageViewModel>(
    FragmentLanguageBinding::inflate, LanguageViewModel::class.java
), BackPressHandler {
    private val languageAdapter by lazy { LanguageAdapter(requireContext()) }
    private var isFromSetting = false

    override fun onBackPressed(): Boolean {

        when {
            isFromSetting -> {
                toSettingFromLang()
            }
            isLanuageScreen() -> {
                popBack()
            }
            else -> {
                System.exit(0)
                requireActivity().finish()
            }
        }
        return true
    }
    override fun setupPreViews() {

        val isFirst = !SharedPreferencesManager.isLanuageScreen()
        if (!isFirst) {
            binding.imageBgLang.setImageResource(R.drawable.img_bg_home)
        }


        binding.recycleLanguage.apply {
            adapter = languageAdapter
            itemAnimator = null
        }

        val currentLang = SharedPreferencesManager.isLanguageKey()
        viewModel.setFirstLanguage(isFirst = isFirst)
        viewModel.loadLanguages(currentLang)

        val list = viewModel.languageList.value
        if (list.isNotEmpty()) {
            languageAdapter.submitList(list)
        }
    }
    private fun updateActionBar(isFirst: Boolean) {
        binding.apply {
            if (isFirst) {
                actionBar.btnActionBarRight.invisible()
                actionBar.btnActionBarRight.setImageResource(R.drawable.select_language)
            } else {
                actionBar.btnActionBarLeft.visible()
                actionBar.btnActionBarRight.setImageResource(R.drawable.select_language)
                // ❌ Xóa Glide — dùng setImageResource trực tiếp
                imageBgLang.setImageResource(R.drawable.img_bg_home)
            }
        }
    }
    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarRight.onClick {
                handleDone()
            }
            actionBar.btnActionBarLeft.onClick( 500) {
                // Dùng chung logic với onBackPressed
                onBackPressed()
            }
        }
        handleRcv()
    }

    override fun inflateBinding(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): FragmentLanguageBinding = FragmentLanguageBinding.inflate(inflater, container, false)

    override fun initView() {
        isFromSetting = findNavController().previousBackStackEntry?.destination?.id == R.id.setting
        Admob.getInstance().loadNativeAd(
            requireContext(),
            getString(R.string.native_language),
            binding.nativeAds,
            R.layout.ads_native_big_btn_top
        )
        binding.actionBar.apply {
            btnActionBarRight.gone()
            btnActionBarLeft.setImageResource(R.drawable.back_app)
        }
        binding.layoutTitle.txtLang.isSelected = true

        // ✅ Bỏ initRcv() — đã làm trong setupPreViews
        updateActionBar(viewModel.isFirstLanguage.value)
    }


    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isFirstLanguage.collect { isFirst ->
                        updateActionBar(isFirst)
                    }
                }
                launch {
                    viewModel.languageList.collect { list ->
                        if (list.isNotEmpty()) {
                            languageAdapter.submitList(list)
                        }
                    }
                }
                launch {
                    viewModel.codeLang.collect { code ->
                        if (code.isNotEmpty()) {
                            binding.actionBar.btnActionBarRight.visible()
                        }
                    }
                }
            }
        }
    }



    override fun bindViewModel() {
    }

    private fun initRcv() {
        binding.recycleLanguage.apply {
            adapter = languageAdapter
            itemAnimator = null
            post {
                background = ContextCompat.getDrawable(requireContext(), R.drawable.img_bg_rcy_lang)
            }
        }
    }
    private fun handleRcv() {
        binding.apply {
            languageAdapter.onItemClick = { code ->
                binding.actionBar.btnActionBarRight.visible()
                viewModel.selectLanguage(code)
            }
        }
    }

    private fun handleDone() {
        val code = viewModel.codeLang.value
        if (code.isEmpty()) {
            showToast(R.string.not_select_lang)
            return
        }

        sharedPreferences.setLanguageKey(code)
        setLocale(requireContext(), code)
       updateLanguage(code)

        if (viewModel.isFirstLanguage.value) {
            sharedPreferences.setLanuageScreen(true)
            Log.d("LANG", "Navigating to Intro")
            toIntroFromLanguage()
        }  else {
        // Update locale cho Activity context ngay lập tức
        val locale = Locale(code)
        val config = Configuration(requireActivity().resources.configuration)
        config.setLocale(locale)
        requireActivity().resources.updateConfiguration(config, requireActivity().resources.displayMetrics)

        // Rồi mới navigate
        toHomeFromLanguage()
    }
    }
}