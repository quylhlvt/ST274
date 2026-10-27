package com.oc.catemoji.catoc

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.oc.catemoji.catoc.core.extention.OuterStrokeShadownTextView
import com.oc.catemoji.catoc.core.helper.LanguageHelper
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp                     // QUAN TRỌNG NHẤT – KHÔNG ĐƯỢC THIẾU
class MyApplication : Application() {
    private val screenViewModelStore = ViewModelStore()

    // Every Activity uses the same process-scoped state, including restored screens.
    fun sharedViewModel(owner: AppCompatActivity): ViewModelActivity = ViewModelProvider(
        screenViewModelStore,
        owner.defaultViewModelProviderFactory,
        owner.defaultViewModelCreationExtras
    )[ViewModelActivity::class.java]

    override fun onCreate() {
        super.onCreate()
        isTablet = resources.configuration.smallestScreenWidthDp >= 600
        getSharedPreferences("DEFAULT", MODE_PRIVATE).also { preferences ->
            SharedPreferencesManager.sharedPreferences = preferences
            SharedPreferencesManager.editor = preferences.edit()
        }
        LanguageHelper.setLocale(this)
//        Admob.getInstance().setTimeLimitShowAds(30_000)
////        Admob.getInstance().setOpenShowAllAds(false)
//        Admob.getInstance().setTimeCountdownNativeCollab(20_000)
        val mmkvDir = java.io.File(filesDir, "mmkv_store").also { it.mkdirs() }
        MMKV.initialize(this, mmkvDir.absolutePath)
//        AppOpenManager.getInstance().disableAppResumeWithActivity(SplashActivity::class.java)
        Log.d("MyApplication", "MMKV initialized at: ${mmkvDir.absolutePath}")
        Thread {
            try {
                // 1. Font — giảm ~100-300ms cho lần đầu
                ResourcesCompat.getFont(this, R.font.baloo2_extrabold)
                // 2. Drawable — thread-safe
                ContextCompat.getDrawable(this, R.drawable.img_bg_home1)
                ContextCompat.getDrawable(this, R.drawable.img_title_home)
            } catch (e: Exception) {
                Log.e("MyApplication", "Warm up error: ${e.message}")
            }

            // 3. OuterStrokeShadownTextView — main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    OuterStrokeShadownTextView(this).apply {
                        typeface = ResourcesCompat.getFont(
                            this@MyApplication, R.font.baloo2_extrabold
                        )
                    }
                } catch (e: Exception) { }
            }
        }.start()
    }


    companion object {
        var isTablet: Boolean = false
            private set

        val editorGridSpanCount: Int
            get() = if (isTablet) 7 else 5

        val speechGridSpanCount: Int
            get() = if (isTablet) 6 else 4
    }
}
