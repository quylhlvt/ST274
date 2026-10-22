package com.oc.catemoji.catoc.ui.main.createPony

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.logEvent
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.databinding.ActivityChooseCatBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeActivity
import com.oc.catemoji.catoc.ui.main.pause.PauseOCChallengeActivity
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ChooseCatActivity : BaseActivity<ActivityChooseCatBinding, ChoosePonyViewModel>(
    ActivityChooseCatBinding::inflate,
    ChoosePonyViewModel::class.java
) {
    private lateinit var adapter: ChoosePonyAdapter
    private var firstLoad = true
    private val isPauseChallenge by lazy {
        intent.getBooleanExtra(EXTRA_PAUSE_CHALLENGE, false)
    }

    private fun initNativeCollab() {
        val nativeAdId = if (isPauseChallenge) {
            R.string.native_cl_categoryPauOC
        } else {
            R.string.native_cl_category
        }
        Admob.getInstance().loadNativeCollapNotBanner(
            this@ChooseCatActivity,
            getString(nativeAdId),
            binding.flNativeCollab
        )
    }

    override fun onRestart() {
        super.onRestart()
        initNativeCollab()
    }

    override fun initView() {
        hideGlobalDialogSafe()
        initNativeCollab()
        setImageActionBar(binding.actionBar.btnActionBarLeft, R.drawable.back_app)
        val titleRes = if (isPauseChallenge) {
            R.string.pause_oc_challenge
        } else {
            R.string.category
        }
        setTextActionBar(binding.actionBar.tvCenter, getString(titleRes))
        adapter = ChoosePonyAdapter(::onCharacterClick)
        binding.recycleChoose.apply {
            layoutManager = GridLayoutManager(this@ChooseCatActivity, 2)
            adapter = this@ChooseCatActivity.adapter
            itemAnimator = null
        }
    }

    private fun onCharacterClick(character: CustomModel, position: Int) {
        if (!isInternetAvailable(this)) {
            showUnstableNetworkDialog()
            return
        }
        lifecycleScope.launch {
            val connected = withContext(Dispatchers.IO) { isNetworkConnected(this@ChooseCatActivity) }
            if (!connected) {
                showUnstableNetworkDialog()
                return@launch
            }
            val templates = viewModelActivity.templates.value
            val index = templates.indexOfFirst { it.id == character.id }
            if (index < 0 || position < 0) {
                android.widget.Toast.makeText(
                    this@ChooseCatActivity,
                    R.string.download_failed_please_try_again_later,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val destination = if (isPauseChallenge) {
                PauseOCChallengeActivity::class.java
            } else {
                CustomizeActivity::class.java
            }
            val nextIntent = Intent(this@ChooseCatActivity, destination).apply {
                putExtra(CustomizeActivity.ARG_TEMPLATE_INDEX, index)
                putExtra(CustomizeActivity.ARG_TEMPLATE_ID, character.id)
                if (destination == PauseOCChallengeActivity::class.java) {
                    putExtra("SHOW_TOUCH_GUIDE", true)
                }
            }
            if (isPauseChallenge) {
                startActivity(nextIntent)
            } else {
                showInter {
                    val dataName = character.id
                        .removePrefix("online_")
                        .removePrefix("template_")
                    Log.d("logevent2", "click_item_$dataName - ${character.avatar}")
                    logEvent("click_item_$dataName")
                    startActivity(nextIntent)
                }
            }
        }
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { handleBackNavigation() }
    }

    override fun onScreenBackPressed(): Boolean {
        handleBackNavigation()
        return true
    }

    private fun handleBackNavigation() {
        if (isPauseChallenge) {
            showInter { finish() }
        } else {
            finish()
        }
    }

    companion object {
        const val EXTRA_PAUSE_CHALLENGE = "pause_challenge"
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    kotlinx.coroutines.flow.combine(
                        viewModelActivity.templates,
                        viewModelActivity.isFetchingOnlineFlow,
                        viewModel.cacheLoaded
                    ) { templates, fetching, cached -> Triple(templates, fetching, cached) }
                        .collect { (templates, fetching, cached) ->
                            val online = withContext(Dispatchers.IO) { isInternetAvailable(this@ChooseCatActivity) }
                            viewModel.updateFilteredTemplates(templates, online)
                            if (firstLoad && !fetching && cached) {
                                firstLoad = false
                                if (viewModel.filteredTemplates.value.isEmpty() && !online) showNoInternetDialog()
                            }
                        }
                }
                launch {
                    viewModel.networkAvailable.drop(1).filter { it }.collect {
                        val templates = viewModelActivity.templates.value
                        viewModel.updateFilteredTemplates(templates, true)
                        adapter.submitList(viewModel.filteredTemplates.value)
                        if (templates.none { it.id.startsWith("online_") } && !viewModelActivity.isFetchingOnlineFlow.value) {
                            viewModelActivity.fetchOnlineTemplates()
                        }
                    }
                }
                launch { viewModel.filteredTemplates.collect { if (it.isNotEmpty()) adapter.submitList(it) } }
                launch { viewModelActivity.error.collect { it?.let { message -> showSnackbar(message) } } }
            }
        }
    }

}
