package com.oc.catemoji.catoc.core.helper

import android.app.WallpaperManager
import android.content.Context
import com.oc.catemoji.catoc.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object WallpaperHelper {
    suspend fun setWallpaper(context: Context, path: String, flags: Int): Int =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.isFile) return@withContext R.string.errorcontent
            val manager = WallpaperManager.getInstance(context)
            if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
                return@withContext R.string.wallpaper_set_failed
            }
            val wallpaperId = file.inputStream().use { manager.setStream(it, null, true, flags) }
            if (wallpaperId != 0) R.string.wallpaper_set_success else R.string.wallpaper_set_failed
        }
}
