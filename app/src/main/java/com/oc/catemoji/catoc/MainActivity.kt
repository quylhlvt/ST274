package com.oc.catemoji.catoc

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.oc.catemoji.catoc.core.extention.hideNavigation
import com.oc.catemoji.catoc.ui.main.home.HomeActivity
import com.oc.catemoji.catoc.ui.onboarding.splash.SplashActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger

/** Dispatches launches; screen Activities own their windows and back stack. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideNavigation(true)
        val sharedViewModel = (application as MyApplication).sharedViewModel(this)
        preloadOnboardingImages(sharedViewModel)
        val destination = if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) {
            HomeActivity::class.java
        } else {
            SplashActivity::class.java
        }
        startActivity(Intent(this, destination).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun preloadOnboardingImages(sharedViewModel: ViewModelActivity) {
        if (sharedViewModel.imagesReady.value) return
        val images = listOf(
            R.drawable.ic_flag_hindi,
            R.drawable.ic_flag_spanish,
            R.drawable.ic_flag_french,
            R.drawable.ic_flag_english,
            R.drawable.ic_flag_portugeese,
            R.drawable.ic_flag_indo,
            R.drawable.ic_flag_germani,
            R.drawable.img_title_home,
            R.drawable.img_bg_lang,
            R.drawable.img_bg_home1,
            R.drawable.img_bg_rcy_lang
        )
        val remaining = AtomicInteger(images.size)
        images.forEach { resource ->
            // Application requests continue after this launcher Activity finishes.
            Glide.with(applicationContext)
                .load(resource)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(object : CustomTarget<Drawable>() {
                    private fun complete() {
                        if (remaining.decrementAndGet() == 0) sharedViewModel.notifyImagesReady()
                    }
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) = complete()
                    override fun onLoadFailed(errorDrawable: Drawable?) = complete()
                    override fun onLoadCleared(placeholder: Drawable?) = Unit
                })
        }
    }

    companion object {
        const val EXTRA_OPEN_HOME = "open_home"
    }
}
