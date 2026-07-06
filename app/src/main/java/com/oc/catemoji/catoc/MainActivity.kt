package com.oc.catemoji.catoc

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
import com.google.firebase.analytics.FirebaseAnalytics
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.core.base.BackPressHandler
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.oc.catemoji.catoc.databinding.DialogbaseBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
interface LoadingController {
    fun showGlobalLoading()
    fun hideGlobalLoading()
    fun showGlobalConfirmDialog(
        message: String,
        title: String? = null,
        onYes: () -> Unit,
        onNo: (() -> Unit)? = null
    )
    fun showGlobalOkDialog(
        message: String,
        title: String? = null,
        onOk: (() -> Unit)? = null
    )
    fun hideGlobalDialog()
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() , LoadingController{
    private var globalLoadingDialog: Dialog? = null
    private var globalConfirmDialog: Dialog? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun showGlobalLoading() {
        if (globalLoadingDialog?.isShowing == true) return
        runOnUiThread {
            globalLoadingDialog = buildDialog(
                message = getString(R.string.loading),
                showButtons = false,
                cancelable = false
            )
            globalLoadingDialog?.show()
            hideNavigation(true)
        }
    }
    override fun showGlobalOkDialog(message: String, title: String?, onOk: (() -> Unit)?) {
        runOnUiThread {
            globalConfirmDialog?.dismiss()
            globalConfirmDialog = buildDialog(
                message = message,
                title = title,
                showButtons = true,
                cancelable = true,
                onYes = {
                    globalConfirmDialog?.dismiss()
                    globalConfirmDialog = null
                    onOk?.invoke()
                }
            ).also { dialog ->
                dialog.findViewById<TextView>(R.id.txtYes)?.text = getString(R.string.ok)
                dialog.findViewById<View>(R.id.btnNo)?.gone()
            }
            globalConfirmDialog?.show()
            hideNavigation(true)
        }
    }

    override fun hideGlobalDialog() {
        runOnUiThread {
            globalConfirmDialog?.dismiss()
            globalConfirmDialog = null
            hideNavigation(true)
        }
    }
    override fun hideGlobalLoading() {
        val stack = Thread.currentThread().stackTrace
            .take(8).joinToString("\n") { it.toString() }
        android.util.Log.e("LOADING", "hideGlobalLoading called!\n$stack")

        runOnUiThread {
            globalLoadingDialog?.dismiss()
            globalLoadingDialog = null
            hideNavigation(true)
        }
    }

    override fun showGlobalConfirmDialog(
        message: String,
        title: String?,
        onYes: () -> Unit,
        onNo: (() -> Unit)?
    ) {
        runOnUiThread {
            globalConfirmDialog?.dismiss()
            globalConfirmDialog = buildDialog(
                message = message,
                title = title,
                showButtons = true,
                cancelable = true,
                onYes = {
                    globalConfirmDialog?.dismiss()
                    globalConfirmDialog = null
                    onYes()
                },
                onNo = {
                    globalConfirmDialog?.dismiss()
                    globalConfirmDialog = null
                    onNo?.invoke()
                }
            )
            globalConfirmDialog?.show()
            hideNavigation(true)
        }
    }

    // Builder dùng chung
    private fun buildDialog(
        message: String,
        title: String? = null,
        showButtons: Boolean = false,
        cancelable: Boolean = false,
        onYes: (() -> Unit)? = null,
        onNo: (() -> Unit)? = null
    ): Dialog {
        return Dialog(this, R.style.BaseDialog).apply {
            val binding = DialogbaseBinding.inflate(layoutInflater)
            setContentView(binding.root)
            binding.txtYes.isSelected = true
            binding.txtNo.isSelected = true
            title?.let { binding.txtTitle.text = it }
            binding.txtContent.text = message

            if (showButtons) {
                binding.btnYes.visible()
                binding.btnNo.visible()
                binding.btnYes.setOnClickListener { onYes?.invoke() }
                binding.btnNo.setOnClickListener { onNo?.invoke() }
                binding.txtContent.visible()
                binding.animationView.gone()
            } else {
                binding.btnYes.gone()
                binding.btnNo.gone()
                binding.txtContent.gone()
                binding.animationView.visible()
            }

            setCancelable(cancelable)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setGravity(Gravity.CENTER)
            }
        }
    }

    private fun preloadHomeDrawables(onDone: () -> Unit) {
        val flagResIds = listOf(
            R.drawable.ic_flag_hindi,
            R.drawable.ic_flag_spanish,
            R.drawable.ic_flag_french,
            R.drawable.ic_flag_english,
            R.drawable.ic_flag_portugeese,
            R.drawable.ic_flag_indo,
            R.drawable.ic_flag_germani,
            R.drawable.ic_select_lang,
            R.drawable.ic_un_select_lang,
            R.drawable.select_language,
            R.drawable.back_app,
        )

        val bgResIds = listOf(
            R.drawable.img_bg_home,
            R.drawable.img_title_home,
            R.drawable.img_bg_lang,
            R.drawable.img_bg_home1,
            R.drawable.img_bg_rcy_lang,
            R.drawable.img_avatar1,
            R.drawable.img_avatar2,
            R.drawable.img_avatar3,
            R.drawable.img_avatar4,
        )

        val total = flagResIds.size + bgResIds.size
        val doneCount = java.util.concurrent.atomic.AtomicInteger(0)
        val checkDone = { if (doneCount.incrementAndGet() == total) onDone() }

        // ✅ Flag icons — nhỏ, dùng override nhỏ + memory cache
        flagResIds.forEach { resId ->
            Glide.with(this)
                .load(resId)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(64, 64)           // ← giảm xuống 64 cho flag
                .preload()                  // ← preload vào memory cache
            checkDone()                     // ← không cần đợi callback
        }

        bgResIds.forEach { resId ->
            Glide.with(this)
                .load(resId)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(SIZE_ORIGINAL)
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(resource: android.graphics.drawable.Drawable, transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?) { checkDone() }
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) { checkDone() }
                })

    }
    }
    private lateinit var navController: NavController
    private val mainViewModel: ViewModelActivity by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("PERF2", "onCreate START: ${System.currentTimeMillis()}")
        super.onCreate(savedInstanceState)
        Log.d("PERF2", "super.onCreate done: ${System.currentTimeMillis()}")

        setContentView(R.layout.activity_main)
        Log.d("PERF2", "setContentView done: ${System.currentTimeMillis()}")
        Admob.getInstance().setTimeLimitShowAds(30000)
        Admob.getInstance().setTimeCountdownNativeCollab(15000)
        hideNavigation(true)        // ← sau setContentView, window đã sẵn sàng
        initSharedPreferences()
//        applyLanguage()
        Admob.getInstance().loadInterAll(this, getString(R.string.inter_all))

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        navController.addOnDestinationChangedListener { _, destination,_  ->
            val screenName = destination.label?.toString() ?: resources.getResourceEntryName(destination.id)
            val screenClass = if (destination is FragmentNavigator.Destination) {
                destination.className.substringAfterLast(".")
            } else {
                screenName
            }
            Log.d("SCREEN", "$screenName - $screenClass")
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }

            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = navHostFragment.childFragmentManager.primaryNavigationFragment
                if (currentFragment is BackPressHandler && currentFragment.onBackPressed()) return
                if (!navController.popBackStack()) finish()
            }
        })

        window.decorView.post {
            preloadHomeDrawables {
                runOnUiThread {
                    mainViewModel.notifyImagesReady()
                }
            }
        }

        Log.d("PERF2", "onCreate END: ${System.currentTimeMillis()}")
    }
    private fun initSharedPreferences() {
        val sharedPrefs = getSharedPreferences("DEFAULT", Context.MODE_PRIVATE)
        SharedPreferencesManager.sharedPreferences = sharedPrefs
        SharedPreferencesManager.editor = sharedPrefs.edit()
    }
    override fun attachBaseContext(newBase: Context) {
        val sharedPrefs = newBase.getSharedPreferences("DEFAULT", Context.MODE_PRIVATE)
        val lang = sharedPrefs.getString("language_key", "") ?: ""

        val locale = Locale(lang.ifEmpty { "en" })
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }
//    private fun applyLanguage() {
//        val savedLanguage = SharedPreferencesManager.isLanguageKey()
//        if (savedLanguage.isNotEmpty()) {
//            val locale = Locale(savedLanguage)
//            Locale.setDefault(locale)
//
//            val config = Configuration(resources.configuration)
//            config.setLocale(locale)
//
//            // ✅ QUAN TRỌNG: Update configuration
//            resources.updateConfiguration(config, resources.displayMetrics)
//        }
//    }
//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//
//        // ✅ Re-apply language khi xoay màn hình
//        applyLanguage()
//    }

//    // QUAN TRỌNG: xử lý nút Back đúng cách
//    override fun onBackPressed() {
//        if (!navController.popBackStack()) {
//            // Không còn gì trong back stack → thoát app
//            super.onBackPressed()
//        }
//    }

    // Nếu bạn dùng Toolbar + NavigationIcon (mũi tên back)
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        hideNavigation(true)
    }
}
