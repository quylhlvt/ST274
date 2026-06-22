
package com.oc.catemoji.catoc.ui.main.createPony

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.ironsource.ch
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.logEvent
import com.oc.catemoji.catoc.core.extention.safeNavigate
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.showInter
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.databinding.FragmentChoosePonyBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeFragment.Companion.ARG_TEMPLATE_ID
import com.oc.catemoji.catoc.ui.main.customize.CustomizeFragment.Companion.ARG_TEMPLATE_INDEX
import com.oc.catemoji.catoc.utils.BlockableFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ChoosePonyFragment : BaseFragment<FragmentChoosePonyBinding, ChoosePonyViewModel>(
    FragmentChoosePonyBinding::inflate,
    ChoosePonyViewModel::class.java
) {
    private val mainViewModel: ViewModelActivity by activityViewModels()
    private lateinit var adapter: ChoosePonyAdapter
    private var isFirstLoad = true

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentChoosePonyBinding = FragmentChoosePonyBinding.inflate(inflater, container, false)
    private fun initNativeCollab() {
        Log.d("NativeCollab", "🔄 initNativeCollab called, stack: ${Thread.currentThread().stackTrace[3]}")
        Admob.getInstance().loadNativeCollapNotBanner(requireContext(), getString(R.string.native_cl_category), binding.flNativeCollab)
    }
    override fun onFragmentStart() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = false
        initNativeCollab()
    }

    override fun onFragmentStop() {
        if (!isAdded || isDetached) return
        (binding.flNativeCollab as? BlockableFrameLayout)?.isBlocked = true
        binding.flNativeCollab.removeAllViews()
    }
    override fun initView() {
        initNativeCollab()
        setImageActionBar(binding.actionBar.btnActionBarLeft, R.drawable.back_app)
        setTextActionBar(
            binding.actionBar.tvCenter,
            getString(R.string.category)
        )

        adapter = ChoosePonyAdapter { character, position ->
            val number = character.id.filter { it.isDigit() }
            logEvent("click_item_${number}", character.avatar)
            Log.d("logevent","click_item_${number}- ${character.avatar}" )
            if (character.id.startsWith("online_")) {
                if (!isInternetAvailable(requireContext())) {
                    showUnstableNetworkDialog(); return@ChoosePonyAdapter
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val hasInternet = withContext(Dispatchers.IO) {
                        isNetworkConnected(requireContext())
                    }
                    if (!hasInternet) showUnstableNetworkDialog()
                    else showInter {
                        navigateToCustomize(character, position)
                    }
                }
            } else {
                // ✅ Offline item — verify data tồn tại trước khi navigate
                val safeIndex = mainViewModel.templates.value.indexOfFirst { it.id == character.id }
                if (safeIndex < 0) {
                    showToast(getString(R.string.download_failed_please_try_again_later)); return@ChoosePonyAdapter
                }
                showInter { navigateToCustomize(character, safeIndex)}
            }


        }

        binding.recycleChoose.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter       = this@ChoosePonyFragment.adapter
            itemAnimator  = null
        }
    }
    private fun navigateToCustomize(character: CustomModel, index: Int) {
        val templates = mainViewModel.templates.value
        if (index < 0 || index >= templates.size) {
            showToast(getString(R.string.download_failed_please_try_again_later))
            return
        }
        // Verify khớp
        val correctIndex = if (templates[index].id == character.id) {
            index
        } else {
            templates.indexOfFirst { it.id == character.id }
                .takeIf { it >= 0 }
                ?: run {
                    showToast(getString(R.string.download_failed_please_try_again_later))
                    return
                }
        }
        findNavController().safeNavigate(
            R.id.action_createPony_to_custom,
            bundleOf(
                ARG_TEMPLATE_INDEX to correctIndex,
                ARG_TEMPLATE_ID to character.id  // ✅ Pass thêm id để verify
            )
        )
    }
    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.setOnClickListener { findNavController().navigateUp() }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    kotlinx.coroutines.flow.combine(
                        mainViewModel.templates,
                        mainViewModel.isFetchingOnlineFlow
                    ) { templates, isFetching -> Pair(templates, isFetching) }
                        .collect { (templates, isFetching) ->
                            val hasInternet = withContext(Dispatchers.IO) {
                                isInternetAvailable(requireContext())
                            }

                            if (hasInternet && templates.isNotEmpty()) {
                                viewModel.updateFilteredTemplates(templates, true)
                                adapter.submitList(templates)
                            } else if (!hasInternet) {
                                // ✅ offline: dùng cache, không filter bỏ online_
                                val cached = viewModel.filteredTemplates.value
                                val toShow = cached.ifEmpty { templates.filter { !it.id.startsWith("online_") } }
                                adapter.submitList(toShow)
                            }

                            if (isFirstLoad && !isFetching) {
                                isFirstLoad = false
                                if (adapter.items.isEmpty()) showNoInternetDialog()
                            }
                        }
                }

                launch {
                    viewModel.networkAvailable
                        .drop(1) // bỏ emit initial
                        .filter { it } // chỉ khi available
                        .collect {
                            val templates = mainViewModel.templates.value
                            viewModel.updateFilteredTemplates(templates, true)
                            adapter.submitList(viewModel.filteredTemplates.value)

                            val hasOnline = templates.any { it.id.startsWith("online_") }
                            if (!hasOnline && !mainViewModel.isFetchingOnlineFlow.value) {
                                mainViewModel.fetchOnlineTemplates()
                            }
                        }
                }

                launch {
                    mainViewModel.error.collect { error ->
                        error?.let { showSnackbar(it) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (adapter.items.isEmpty()) {
            val cached = viewModel.filteredTemplates.value
            if (cached.isNotEmpty()) adapter.submitList(cached)
        }
    }

    override fun bindViewModel() {}
}
