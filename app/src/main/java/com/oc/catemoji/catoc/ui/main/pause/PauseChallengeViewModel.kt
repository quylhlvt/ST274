package com.oc.catemoji.catoc.ui.main.pause

import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.ViewModel

class PauseChallengeViewModel : ViewModel() {
    var session: PauseChallengeSession? = null
    val displayedBitmaps = mutableMapOf<Int, Bitmap>()
    var resultPath: String? = null

    // Cached frames own their pixels; releasing a Glide request cannot change a frozen layer.
    val frames = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override fun onCleared() {
        displayedBitmaps.clear()
        frames.evictAll()
    }
}
