package com.oc.catemoji.catoc.core.base

import android.os.Bundle
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.android.material.snackbar.Snackbar
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import com.oc.catemoji.catoc.MyApplication
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.oc.catemoji.catoc.databinding.DialogbaseBinding
import javax.inject.Inject

/** Common lifecycle and binding support for screens implemented as Activities. */
abstract class BaseActivity<VB : ViewBinding, VM : ViewModel>(
    private val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> VB,
    private val viewModelClass: Class<VM>
) : AppCompatActivity() {
    private lateinit var _binding: VB
    private var loadingDialog: Dialog? = null
    private var confirmDialog: Dialog? = null
    protected val binding: VB get() = _binding
    protected val view: View get() = binding.root
    /** Kept as a migration alias for screen code formerly hosted in Fragments. */
    protected val viewLifecycleOwner: androidx.lifecycle.LifecycleOwner get() = this
    protected val arguments: Bundle? get() = intent.extras
    protected val isAdded: Boolean get() = !isFinishing && !isDestroyed
    protected val isDetached: Boolean get() = false
    protected val viewModel: VM by lazy { ViewModelProvider(this)[viewModelClass] }
    protected val viewModelActivity: ViewModelActivity by lazy {
        (application as MyApplication).sharedViewModel(this)
    }
    private var appliedLanguage = ""
    private val router by lazy { ActivityRouter(this) }

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
    protected open fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): VB = bindingInflater(inflater, container, false)

    protected fun requireContext(): android.content.Context = this
    protected fun requireActivity(): AppCompatActivity = this
    protected open fun onScreenBackPressed(): Boolean = false

    protected fun showSnackbar(message: String) = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    protected fun hideGlobalDialogSafe() {
        loadingDialog?.dismiss()
        loadingDialog = null
        confirmDialog?.dismiss()
        confirmDialog = null
    }
    protected fun showLoadingSafe() {
        if (loadingDialog?.isShowing == true) return
        loadingDialog = createScreenDialog(getString(R.string.loading), showButtons = false, cancelable = false)
            .also { it.show() }
    }
    protected fun hideLoadingSafe() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
    protected fun showToast(content: Any) {
        val message = if (content is Int) getString(content) else content.toString()
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    protected fun showNoInternetDialog(onOk: (() -> Unit)? = null) = showInfoDialog(

        message = getString(R.string.please_connect_to_the_internet_to_download_more_data),
        onOk = onOk
    )
    protected fun showUnstableNetworkDialog(onOk: (() -> Unit)? = null) = showInfoDialog(
        message = getString(R.string.unstable_connection_please_check_your_network_connection),
        onOk = onOk
    )
    protected fun showLoadingDataDialog(onOk: (() -> Unit)? = null) = android.app.AlertDialog.Builder(this)
        .setMessage(R.string.please_wait_a_few_seconds_for_data_to_load)
        .setPositiveButton(R.string.ok) { _, _ -> onOk?.invoke() }
        .show()
        .also { it.hideNavigation(true) }
    protected fun showOkDialog(message: String, title: String? = null, onOk: (() -> Unit)? = null) {
        android.app.AlertDialog.Builder(this).apply {
            title?.let(::setTitle)
            setMessage(message)
            setPositiveButton(R.string.ok) { _, _ -> onOk?.invoke() }
        }.show().also { it.hideNavigation(true) }
    }
    protected fun showConfirmDialog(message: String, title: String? = null, onYes: () -> Unit, onNo: (() -> Unit)? = null, yesText: String? = null, noText: String? = null) {
        confirmDialog?.dismiss()
        confirmDialog = createScreenDialog(
            message = message,
            title = title,
            showButtons = true,
            cancelable = true,
            yesText = yesText,
            noText = noText,
            onYes = onYes,
            onNo = onNo
        ).also { it.show() }
    }

    private fun showInfoDialog(message: String,tittle : Boolean?=null, onOk: (() -> Unit)? = null): Dialog {
        confirmDialog?.dismiss()
        return createScreenDialog(
            message = message,
            title = if (tittle==null)getString(R.string.internet)else getString(R.string.notification),
            showButtons = true,
            cancelable = true,
            singleButtonText = getString(R.string.ok),
            onYes = onOk
        ).also {
            confirmDialog = it
            it.show()
        }
    }

    private fun createScreenDialog(
        message: String,
        title: String? = null,
        showButtons: Boolean,
        cancelable: Boolean,
        yesText: String? = null,
        noText: String? = null,
        singleButtonText: String? = null,
        onYes: (() -> Unit)? = null,
        onNo: (() -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(this, R.style.BaseDialog)
        val dialogBinding = DialogbaseBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialogBinding.txtYes.isSelected = true
        dialogBinding.txtNo.isSelected = true
        dialogBinding.txtYes.text = yesText ?: getString(R.string.yes)
        dialogBinding.txtNo.text = noText ?: getString(R.string.no)
        dialogBinding.txtOk.text = singleButtonText ?: getString(R.string.ok)
        title?.let { dialogBinding.txtTitle.text = it }
        dialogBinding.txtContent.text = message
        if (showButtons) {
            val isSingleButton = singleButtonText != null
            dialogBinding.btnYes.visibility = if (isSingleButton) View.GONE else View.VISIBLE
            dialogBinding.btnNo.visibility = if (isSingleButton) View.GONE else View.VISIBLE
            dialogBinding.btnOk.visibility = if (isSingleButton) View.VISIBLE else View.GONE
            dialogBinding.btnYes.setOnClickListener {
                dialog.dismiss()
                onYes?.invoke()
            }
            dialogBinding.btnNo.setOnClickListener {
                dialog.dismiss()
                onNo?.invoke()
            }
            dialogBinding.btnOk.setOnClickListener {
                dialog.dismiss()
                onYes?.invoke()
            }
            dialogBinding.txtContent.visibility = View.VISIBLE
            dialogBinding.animationView.visibility = View.GONE
        } else {
            dialogBinding.btnYes.visibility = View.GONE
            dialogBinding.btnNo.visibility = View.GONE
            dialogBinding.btnOk.visibility = View.GONE
            dialogBinding.txtContent.visibility = View.GONE
            dialogBinding.animationView.visibility = View.VISIBLE
        }
        dialog.setCancelable(cancelable)
        val activitySystemUiVisibility = window.decorView.systemUiVisibility
        dialog.window?.apply {
            // A focusable dialog briefly restores Android's navigation bar before the
            // Activity can hide it again. That changes system-bar insets and makes the
            // editor underneath shrink/expand while loading is shown and dismissed.
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            decorView.systemUiVisibility = activitySystemUiVisibility
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                decorView.systemUiVisibility = activitySystemUiVisibility
                clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
            dialog.hideNavigation(true)
        }
        return dialog
    }
    protected fun activityRouter() = router
    protected fun popBack(): Boolean { finish(); return true }

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
        // Start loading even when Android restores a screen without the launcher.
        viewModelActivity
        logScreenView()
        _binding = inflateBinding(layoutInflater, null, savedInstanceState)
        setContentView(binding.root)
        setupPreViews()
        initView()
        initText()
        onViewCreated(binding.root, savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!this@BaseActivity.onScreenBackPressed()) finish()
            }
        })
        binding.root.post {
            if (isFinishing || isDestroyed) return@post
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

    override fun onStart() {
        super.onStart()
        onScreenStart()
    }

    override fun onStop() {
        onScreenStop()
        super.onStop()
    }

    protected open fun onScreenDestroyed() {}

    override fun onDestroy() {
        hideGlobalDialogSafe()
        onScreenDestroyed()
        super.onDestroy()
    }
}

class ActivityRouter(private val activity: AppCompatActivity) {
    fun navigateUp(): Boolean { activity.finish(); return true }
    fun popBackStack(): Boolean = navigateUp()
    fun safeNavigate(actionId: Int, args: Bundle? = null) = navigate(actionId, args)
    fun navigate(actionId: Int, args: Bundle? = null) {
        if (actionId == R.id.action_successCosplay_back_show) {
            activity.finish()
            return
        }
        val target: Class<out android.app.Activity> = when (actionId) {
            R.id.homeActivity -> com.oc.catemoji.catoc.ui.main.home.HomeActivity::class.java
            R.id.myPony, R.id.action_home_to_myPony, R.id.action_view_to_myPony,
            R.id.action_custom_to_myPony -> com.oc.catemoji.catoc.ui.main.myPony.MyPonyActivity::class.java
            R.id.viewActivity -> com.oc.catemoji.catoc.ui.main.view.ViewActivity::class.java
            R.id.random, R.id.action_home_to_random -> com.oc.catemoji.catoc.ui.main.random.RandomActivity::class.java
            R.id.action_my_pony_to_home -> com.oc.catemoji.catoc.ui.main.home.HomeActivity::class.java
            R.id.action_home_to_createPony -> com.oc.catemoji.catoc.ui.main.createPony.ChooseCatActivity::class.java
            R.id.action_home_to_cosplay -> com.oc.catemoji.catoc.ui.main.cosplay.CosplayActivity::class.java
            R.id.action_home_to_studio -> com.oc.catemoji.catoc.ui.main.studio.CatStudioActivity::class.java
            R.id.action_home_to_setting -> com.oc.catemoji.catoc.ui.main.setting.SettingActivity::class.java
            R.id.action_createPony_to_custom, R.id.action_random_to_custom,
            R.id.action_mypony_to_custom, R.id.action_view_to_customize -> com.oc.catemoji.catoc.ui.main.customize.CustomizeActivity::class.java
            R.id.action_customize_to_addCharacter -> com.oc.catemoji.catoc.ui.main.add_character.AddCharacterActivity::class.java
            R.id.action_addCharacter_to_viewImage,
            R.id.action_mypony_to_view -> com.oc.catemoji.catoc.ui.main.view.ViewActivity::class.java
            R.id.action_cosplay_to_show -> com.oc.catemoji.catoc.ui.main.show.ShowActivity::class.java
            R.id.action_show_to_successCosplay -> com.oc.catemoji.catoc.ui.main.successcosplay.SuccessCosplayActivity::class.java
            R.id.action_successCosplay_to_home -> com.oc.catemoji.catoc.ui.main.home.HomeActivity::class.java
            else -> throw IllegalArgumentException("Unknown Activity action: $actionId")
        }
        val intent = android.content.Intent(activity, target).apply {
            args?.let { putExtras(it) }
            if (target == com.oc.catemoji.catoc.ui.main.home.HomeActivity::class.java ||
                target == com.oc.catemoji.catoc.ui.main.myPony.MyPonyActivity::class.java
            ) {
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
        if (target == com.oc.catemoji.catoc.ui.main.myPony.MyPonyActivity::class.java &&
            actionId in setOf(R.id.action_view_to_myPony, R.id.action_custom_to_myPony)
        ) {
            // Complete the creation flow so Back from the album returns to Home.
            androidx.core.app.TaskStackBuilder.create(activity)
                .addNextIntent(android.content.Intent(activity, com.oc.catemoji.catoc.ui.main.home.HomeActivity::class.java))
                .addNextIntent(intent)
                .startActivities()
            return
        }
        activity.startActivity(intent)
    }
}
