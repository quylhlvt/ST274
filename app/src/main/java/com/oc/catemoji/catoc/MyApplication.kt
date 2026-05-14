package com.oc.catemoji.catoc

import android.app.Application
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.oc.catemoji.catoc.core.extention.OuterStrokeShadownTextView
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp                     // QUAN TRỌNG NHẤT – KHÔNG ĐƯỢC THIẾU
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val mmkvDir = java.io.File(filesDir, "mmkv_store").also { it.mkdirs() }
        MMKV.initialize(this, mmkvDir.absolutePath)
        Log.d("MyApplication", "MMKV initialized at: ${mmkvDir.absolutePath}")
        Thread {
            try {
                // 1. Font — giảm ~100-300ms cho lần đầu
                ResourcesCompat.getFont(this, R.font.baloo2_extrabold)

                // 2. Drawable — thread-safe
                ContextCompat.getDrawable(this, R.drawable.img_bg_home1)
                ContextCompat.getDrawable(this, R.drawable.img_bg_tv1)
                ContextCompat.getDrawable(this, R.drawable.img_bg_home)
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