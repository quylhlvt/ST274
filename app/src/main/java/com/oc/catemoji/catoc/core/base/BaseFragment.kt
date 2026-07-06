package com.oc.catemoji.catoc.core.base


import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.oc.catemoji.catoc.LoadingController
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.oc.catemoji.catoc.databinding.DialogbaseBinding
import com.google.android.material.snackbar.Snackbar
import com.lvt.ads.util.Admob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.getValue

abstract class BaseFragment<VB : ViewBinding, VM : ViewModel>(
    private val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> VB,
    private val viewModelClass: Class<VM>
) : Fragment() {
    private var isFirstStart = true

    private lateinit var _binding: VB
    protected val binding: VB get() = _binding
    private var dialog: Dialog? = null
    private var confirmDialogBinding: DialogbaseBinding? = null
    var onYesClick: (() -> Unit)? = null
    var onNoClick: (() -> Unit)? = null
    protected val viewModelActivity: ViewModelActivity by activityViewModels()
    protected val viewModel: VM by lazy {
        ViewModelProvider(this)[viewModelClass]
    }
    private val loadingController: LoadingController?
        get() = activity as? LoadingController

    open fun setupPreViews() {}
    abstract fun viewListener()
    protected var toast: Toast? = null

    @Inject
    internal lateinit var sharedPreferences: SharedPreferencesManager
    abstract fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("PERF2", "onCreateView ${this::class.simpleName}: ${System.currentTimeMillis()}")

        Log.v(TAG, "onCreateView: $this")
        _navController = findNavController()
        _binding = inflateBinding(inflater, container, savedInstanceState)
        setupPreViews()
        return binding.root
    }
    open fun onFragmentStart() {}
    open fun onFragmentStop() {}
//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//
//        // ✅ Re-apply language
//        val savedLanguage = sharedPreferences.isLanguageKey()
//        if (savedLanguage.isNotEmpty()) {
//            val locale = Locale(savedLanguage)
//            Locale.setDefault(locale)
//
//            val config = Configuration(requireContext().resources.configuration)
//            config.setLocale(locale)
//
//            requireContext().resources.updateConfiguration(
//                config,
//                requireContext().resources.displayMetrics
//            )
//        }
//    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PERF2", "onViewCreated ${this::class.simpleName}: ${System.currentTimeMillis()}")

        initView()
        initText()
        binding.root.post {
            if (!isAdded) return@post
            viewListener()
            bindViewModel()
            observeData()
            observeNetworkRetry()
        }
    }

    private fun observeNetworkRetry() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModelActivity.networkOnline
                    .filter { it } // chỉ quan tâm khi online = true
                    .collect {
                        val hasOnlineData = viewModelActivity.templates.value
                            .any { it.id.startsWith("online_") }
                        if (!hasOnlineData) {
                            viewModelActivity.fetchOnlineTemplates()
                        }
                    }
                viewModelActivity.loadBackgroundsAndStickers()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        Log.v(TAG, "onAttach: $this")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "onCreate: $this")
    }

    override fun onStart() {
        super.onStart()
        if (isFirstStart) {
            isFirstStart = false
        } else {
            onFragmentStart()
        }
        Log.v(TAG, "onStart: $this")
    }

    override fun onResume() {
        super.onResume()
        Log.v(TAG, "onResume: $this")
    }

    override fun onPause() {
        super.onPause()
        Log.v(TAG, "onPause: $this")
    }

    override fun onStop() {
        super.onStop()
        onFragmentStop()
        Log.v(TAG, "onStop: $this")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFirstStart = true
        Log.v(TAG, "onDestroyView: $this")
        _navController = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.v(TAG, "onDestroy: $this")
    }

    override fun onDetach() {
        super.onDetach()
        Log.v(TAG, "onDetach: $this")
    }

    private var _navController: NavController? = null

    protected val navController: NavController? get() = _navController
    open fun initView() {}
    open fun initText() {}
    open fun observeData() {}

    fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    fun showToast(content: Any) {
        if (toast != null) {
            toast?.cancel()
        }
        val contentString = when (content) {
            is String -> content
            is Int -> getString(content)
            else -> {
                ""
            }
        }
        toast = Toast.makeText(requireContext(), contentString, Toast.LENGTH_SHORT)
        toast?.show()
    }

    fun hideLoading() {
        dialog?.dismiss()
        dialog = null
        requireActivity().hideNavigation(true)
    }

    fun showLoadingSafe() {
        if (!isAdded || activity == null) return
        loadingController?.showGlobalLoading()
    }

    fun hideLoadingSafe() {
        if (!isAdded || activity == null) return
        loadingController?.hideGlobalLoading()
    }

    fun hideGlobalDialogSafe() {
        if (!isAdded || activity == null) return
        loadingController?.hideGlobalDialog()
    }

// ========== Confirm Dialog ==========

    fun showConfirmDialog(
        message: String,
        title: String? = null,
        onYes: () -> Unit,
        onNo: (() -> Unit)? = null
    ) {
        if (!isAdded || activity == null) return
        loadingController?.showGlobalConfirmDialog(
            message = message,
            title = title,
            onYes = onYes,
            onNo = onNo
        )
    }
    fun showNoInternetDialog(onOk: (() -> Unit)? = null) {
        showOkDialog(
            title = getString(R.string.no_internet),
            message = getString(R.string.please_connect_to_the_internet_to_download_more_data),
            onOk = onOk
        )
    }
    fun showLoadingDataDialog(onOk: (() -> Unit)? = null) {
        showOkDialog(
            title = getString(R.string.internet),
            message = getString(R.string.please_wait_a_few_seconds_for_data_to_load),
            onOk = onOk
        )
    }
    fun showUnstableNetworkDialog(onOk: (() -> Unit)? = null) {
        showOkDialog(
            title = getString(R.string.internet),
            message = getString(R.string.unstable_connection_please_check_your_network_connection),
            onOk = onOk
        )
    }

    fun checkNetworkAndShowDialog() {
        val context = requireContext()
        when {
            !isInternetAvailable(context) -> showNoInternetDialog()
            !isNetworkConnected(context) -> showUnstableNetworkDialog()
            else -> {}
        }
    }
    fun showOkDialog(
        message: String,
        title: String? = null,
        onOk: (() -> Unit)? = null
    ) {
        if (!isAdded || activity == null) return
        (activity as? LoadingController)?.showGlobalOkDialog(
            message = message,
            title = title,
            onOk = onOk
        )
    }
    abstract fun bindViewModel()
}
