package com.oc.catemoji.catoc.core.base

import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.android.material.snackbar.Snackbar
import android.content.Context
import android.content.res.Configuration
import com.oc.catemoji.catoc.MyApplication
import java.util.Locale
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import javax.inject.Inject

/** Common lifecycle and binding support for screens implemented as Activities. */
abstract class BaseActivity<VB : ViewBinding, VM : ViewModel>(
    private val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> VB,
    private val viewModelClass: Class<VM>
) : AppCompatActivity() {
    private lateinit var _binding: VB
    protected val binding: VB get() = _binding
    protected val view: View get() = binding.root
    /** Kept as a migration alias for screen code formerly hosted in Fragments. */
    protected val viewLifecycleOwner: androidx.lifecycle.LifecycleOwner get() = this
    protected val arguments: Bundle? get() = intent.extras
    protected val isAdded: Boolean get() = !isFinishing && !isDestroyed
    protected val isDetached: Boolean get() = false
    protected val viewModelActivity: ViewModelActivity by lazy {
        (application as MyApplication).sharedViewModel(this)
    }
    protected val viewModel: VM by lazy { ViewModelProvider(this)[viewModelClass] }
    private var appliedLanguage = ""
    private var restoredRetainedViewState = false

    @Inject
    internal lateinit var sharedPreferences: SharedPreferencesManager

    protected open fun setupPreViews() {}
    protected open fun initView() {}
    protected open fun initText() {}
    protected open fun viewListener() {}
    protected open fun bindViewModel() {}
    protected open fun observeData() {}
    protected open fun onScreenStart() {}
    protected open fun onScreenStop() {}
    protected open fun onScreenConfigurationChanged(newConfig: Configuration) {}
    /**
     * Retains lightweight View hierarchy state while the app process is alive.
     * Domain data and large objects must still live in Room or a ViewModel.
     */
    protected open val retainScreenViewState: Boolean get() = true
    protected open fun retainedScreenStateKey(): String = javaClass.name
    protected fun clearRetainedScreenState() {
        retainedViewStates.remove(retainedScreenStateKey())
    }
    protected open fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): VB = bindingInflater(inflater, container, false)

    protected fun requireContext(): android.content.Context = this
    protected fun requireActivity(): AppCompatActivity = this
    protected open fun onScreenBackPressed(): Boolean = false

    protected fun showSnackbar(message: String) = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    protected fun showToast(content: Any) {
        val message = if (content is Int) getString(content) else content.toString()
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * Lưu và áp dụng locale ngay trên Activity hiện tại trước khi điều hướng.
     * Nếu Android chưa phản ánh locale mới ngay lập tức, màn gọi có thể recreate
     * và BaseActivity sẽ lấy locale đã commit trong attachBaseContext().
     */
    protected fun updateAppLanguage(languageCode: String): Boolean {
        val locale = Locale.forLanguageTag(languageCode)
        if (languageCode.isBlank() || locale.language.isBlank()) return false

        // setLanguageKey dùng commit=true, nên Activity kế tiếp luôn đọc được giá trị mới.
        SharedPreferencesManager.setLanguageKey(languageCode)
        Locale.setDefault(locale)

        val activityConfig = Configuration(resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(activityConfig, resources.displayMetrics)

        val appResources = applicationContext.resources
        val appConfig = Configuration(appResources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        @Suppress("DEPRECATION")
        appResources.updateConfiguration(appConfig, appResources.displayMetrics)

        appliedLanguage = languageCode
        return isAppLanguageApplied(languageCode)
    }

    protected fun isAppLanguageApplied(languageCode: String): Boolean {
        val expected = Locale.forLanguageTag(languageCode).toLanguageTag()
        val actual = resources.configuration.locales[0].toLanguageTag()
        return SharedPreferencesManager.isLanguageKey() == languageCode &&
            actual.equals(expected, ignoreCase = true)
    }

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = newBase.getSharedPreferences("DEFAULT", Context.MODE_PRIVATE)
            .getString("language_key", "").orEmpty().ifEmpty { "en" }
        val locale = Locale.forLanguageTag(appliedLanguage)
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideNavigation(true)
        logScreenView()
        _binding = inflateBinding(layoutInflater, null, savedInstanceState)
        setContentView(binding.root)
        setupPreViews()
        initView()
        initText()
        onViewCreated(binding.root, savedInstanceState)
        configureRecyclerStateRestoration(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!this@BaseActivity.onScreenBackPressed()) finish()
            }
        })
        binding.root.post {
            if (isFinishing || isDestroyed) return@post
            restoreRetainedViewState(savedInstanceState)
            viewListener()
            bindViewModel()
            observeData()
        }
    }

    protected open fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

    private fun logScreenView() {
        val screenClass = javaClass.simpleName
        FirebaseAnalytics.getInstance(this).logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenClass.removeSuffix("Activity"))
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        hideNavigation(true)
        val language = SharedPreferencesManager.isLanguageKey().ifEmpty { "en" }
        if (language != appliedLanguage) {
            appliedLanguage = language
            recreate()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigation(true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val viewState = if (retainScreenViewState && ::_binding.isInitialized) {
            SparseArray<Parcelable>().also(binding.root::saveHierarchyState)
        } else {
            null
        }
        super.onConfigurationChanged(newConfig)
        hideNavigation(true)
        if (!::_binding.isInitialized) {
            onScreenConfigurationChanged(newConfig)
            return
        }
        binding.root.requestLayout()
        binding.root.invalidate()
        viewState?.let { state ->
            binding.root.post {
                if (!isFinishing && !isDestroyed) {
                    binding.root.restoreHierarchyState(state)
                }
            }
        }
        onScreenConfigurationChanged(newConfig)
    }

    override fun onStart() {
        super.onStart()
        onScreenStart()
    }

    override fun onStop() {
        saveRetainedViewState()
        onScreenStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveRetainedViewState()
        super.onSaveInstanceState(outState)
    }

    protected open fun onScreenDestroyed() {}

    override fun onDestroy() {
        onScreenDestroyed()
        super.onDestroy()
    }

    private fun configureRecyclerStateRestoration(view: View) {
        if (view is RecyclerView) {
            view.adapter?.let { adapter ->
                if (adapter.stateRestorationPolicy == RecyclerView.Adapter.StateRestorationPolicy.ALLOW) {
                    adapter.stateRestorationPolicy =
                        RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                }
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                configureRecyclerStateRestoration(view.getChildAt(index))
            }
        }
    }

    private fun saveRetainedViewState() {
        if (!retainScreenViewState || !::_binding.isInitialized) return
        val state = SparseArray<Parcelable>()
        binding.root.saveHierarchyState(state)
        retainedViewStates[retainedScreenStateKey()] = state
    }

    private fun restoreRetainedViewState(savedInstanceState: Bundle?) {
        if (restoredRetainedViewState || savedInstanceState != null || !retainScreenViewState) return
        restoredRetainedViewState = true
        retainedViewStates[retainedScreenStateKey()]?.let(binding.root::restoreHierarchyState)
    }

    companion object {
        private const val MAX_RETAINED_SCREEN_STATES = 20
        private val retainedViewStates = object :
            LinkedHashMap<String, SparseArray<Parcelable>>(MAX_RETAINED_SCREEN_STATES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, SparseArray<Parcelable>>?,
            ): Boolean = size > MAX_RETAINED_SCREEN_STATES
        }
    }
}
