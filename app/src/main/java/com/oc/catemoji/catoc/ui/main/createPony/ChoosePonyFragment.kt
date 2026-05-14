
package com.oc.catemoji.catoc.ui.main.createPony

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.safeNavigate
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.databinding.FragmentChoosePonyBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeFragment.Companion.ARG_TEMPLATE_ID
import com.oc.catemoji.catoc.ui.main.customize.CustomizeFragment.Companion.ARG_TEMPLATE_INDEX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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

    override fun initView() {
        setImageActionBar(binding.actionBar.btnActionBarLeft, R.drawable.back_app)
        setTextActionBar(
            binding.actionBar.tvCenter,
            getString(R.string.category)
        )

        adapter = ChoosePonyAdapter { character, position ->
            if (character.id.startsWith("online_")) {
                if (!isInternetAvailable(requireContext())) {
                    showUnstableNetworkDialog(); return@ChoosePonyAdapter
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val hasInternet = withContext(Dispatchers.IO) {
                        isNetworkConnected(requireContext())
                    }
                    if (!hasInternet) showUnstableNetworkDialog()
                    else navigateToCustomize(character, position)
                }
            } else {
                // ✅ Offline item — verify data tồn tại trước khi navigate
                val safeIndex = mainViewModel.templates.value.indexOfFirst { it.id == character.id }
                if (safeIndex < 0) {
                    showToast(getString(R.string.download_failed_please_try_again_later)); return@ChoosePonyAdapter
                }
                navigateToCustomize(character, safeIndex)
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
                            // ✅ Lọc theo trạng thái mạng
                            val hasInternet = withContext(Dispatchers.IO) {
                                isInternetAvailable(requireContext())
                            }
                            val filteredTemplates = if (hasInternet) {
                                templates // có mạng → show tất cả
                            } else {
                                templates.filter { !it.id.startsWith("online_") } // mất mạng → chỉ offline
                            }

                            adapter.submitList(filteredTemplates)

                            if (isFirstLoad && !isFetching) {
                                isFirstLoad = false
                                if (filteredTemplates.size <= 1) showNoInternetDialog()
                            }
                        }
                }

                launch {
                    mainViewModel.templates.collect { templates ->
                        val hasOnline = templates.any { it.id.startsWith("online_") }
                        if (!hasOnline && !mainViewModel.isFetchingOnlineFlow.value) {
                            val hasInternet = withContext(Dispatchers.IO) {
                                isInternetAvailable(requireContext())
                            }
                            if (hasInternet) mainViewModel.fetchOnlineTemplates()
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
        viewLifecycleOwner.lifecycleScope.launch {
            val hasInternet = withContext(Dispatchers.IO) {
                isInternetAvailable(requireContext())
            }
            val templates = mainViewModel.templates.value
            val filtered = if (hasInternet) {
                templates
            } else {
                templates.filter { !it.id.startsWith("online_") }
            }
            adapter.submitList(filtered)

            // Fetch online nếu có mạng mà chưa có data online
            if (hasInternet) {
                val hasOnline = templates.any { it.id.startsWith("online_") }
                if (!hasOnline && !mainViewModel.isFetchingOnlineFlow.value) {
                    mainViewModel.fetchOnlineTemplates()
                }
            }
        }
    }

    override fun bindViewModel() {}
}
