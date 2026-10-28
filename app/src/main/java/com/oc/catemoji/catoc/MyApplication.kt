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
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
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
        getSharedPreferences("DEFAULT", MODE_PRIVATE).also { preferences ->
            SharedPreferencesManager.sharedPreferences = preferences
            SharedPreferencesManager.editor = preferences.edit()
        }
        LanguageHelper.setLocale(this)
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


}
