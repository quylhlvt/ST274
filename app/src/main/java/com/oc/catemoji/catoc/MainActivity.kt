package com.oc.catemoji.catoc

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.core.base.SharedActivityViewModels
import com.oc.catemoji.catoc.core.base.MainActivityReference
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
        onNo: (() -> Unit)? = null,
        yesText: String? = null,
        noText: String? = null
    )
    fun showGlobalOkDialog(
        message: String,
        title: String? = null,
        onOk: (() -> Unit)? = null
    )
    fun hideGlobalDialog()
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), LoadingController {
    private var globalLoadingDialog: Dialog? = null
    private var globalConfirmDialog: Dialog? = null

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
                yesText = getString(R.string.ok),
                onYes = {
                    globalConfirmDialog?.dismiss()
                    globalConfirmDialog = null
                    onOk?.invoke()
                }
            ).also { dialog ->
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
        onNo: (() -> Unit)?,
        yesText: String?,
        noText: String?
    ) {
        runOnUiThread {
            globalConfirmDialog?.dismiss()
            globalConfirmDialog = buildDialog(
                message = message,
                title = title,
                showButtons = true,
                cancelable = true,
                yesText = yesText,
                noText = noText,
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
        yesText: String? = null,
        noText: String? = null,
        onYes: (() -> Unit)? = null,
        onNo: (() -> Unit)? = null
    ): Dialog {
        return Dialog(this, R.style.BaseDialog).apply {
            val binding = DialogbaseBinding.inflate(layoutInflater)
            setContentView(binding.root)
            binding.txtYes.isSelected = true
            binding.txtNo.isSelected = true
            binding.txtYes.text = yesText ?: getString(R.string.yes)
            binding.txtNo.text = noText ?: getString(R.string.no)
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
            R.drawable.img_title_home,
            R.drawable.img_bg_lang,
            R.drawable.img_bg_home1,
            R.drawable.img_bg_rcy_lang
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
    private val mainViewModel: ViewModelActivity by viewModels()

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) openHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("PERF2", "onCreate START: ${System.currentTimeMillis()}")
        super.onCreate(savedInstanceState)
        MainActivityReference.instance = this
        Log.d("PERF2", "super.onCreate done: ${System.currentTimeMillis()}")

        // Áp dụng edge-to-edge trước khi inflate layout để tránh vùng trống quanh
        // status bar/tai thỏ xuất hiện trong frame đầu tiên.
        hideNavigation(true)
        setContentView(R.layout.activity_main)

        Admob.getInstance().setTimeLimitShowAds(30000)
        Admob.getInstance().setTimeCountdownNativeCollab(18000)
//        Admob.getInstance().setOpenShowAllAds(false)
        initSharedPreferences()
//        applyLanguage()
        Admob.getInstance().loadInterAll(this, getString(R.string.inter_all))
        SharedActivityViewModels.instance = mainViewModel

        window.decorView.post {
            preloadHomeDrawables {
                runOnUiThread {
                    mainViewModel.notifyImagesReady()
                }
            }
        }

        if (savedInstanceState == null) {
            if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) openHome()
            else startActivity(android.content.Intent(this, com.oc.catemoji.catoc.ui.onboarding.splash.SplashActivity::class.java))
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

    override fun onResume() {
        super.onResume()
        hideNavigation(true)
    }

    override fun onDestroy() {
        if (MainActivityReference.instance === this) MainActivityReference.instance = null
        if (isFinishing && SharedActivityViewModels.instance === mainViewModel) {
            SharedActivityViewModels.instance = null
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // onResume có thể được gọi trước khi màn xin quyền của Facebook đóng hẳn.
            // Chỉ ẩn lại system bars khi app thực sự lấy lại window focus.
            hideNavigation(true)
            window.decorView.postDelayed({
                if (hasWindowFocus()) {
                    hideNavigation(true)
                }
            }, SYSTEM_BAR_RESTORE_DELAY_MS)
        }
    }

    companion object {
        const val EXTRA_OPEN_HOME = "open_home"
        private const val SYSTEM_BAR_RESTORE_DELAY_MS = 300L
    }

    private fun openHome() {
        startActivity(android.content.Intent(this, com.oc.catemoji.catoc.ui.main.home.HomeActivity::class.java))
    }
}
