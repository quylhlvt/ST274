package com.oc.catemoji.catoc.core.helper

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.oc.catemoji.catoc.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// DownloadHelper.kt
object DownloadHelper {

    suspend fun downloadToGallery(
        context: Context,
        path: String,
        subFolder: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val folder = subFolder ?: context.getString(R.string.app_name)
            val src = File(path)
            if (!src.exists()) return@runCatching false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, src.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folder")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@runCatching false

                resolver.openOutputStream(uri)?.use { output ->
                    src.inputStream().use { input -> input.copyTo(output) }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            } else {
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    folder
                )
                picturesDir.mkdirs()
                val dest = File(picturesDir, src.name)
                src.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
            }
            true
        }.getOrDefault(false)
    }

    // Download nhiều file, trả về số lượng thành công
    suspend fun downloadMultipleToGallery(
        context: Context,
        paths: List<String>,
        subFolder: String? = null
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {  // Pair(success, total)
        val folder = subFolder ?: context.getString(R.string.app_name)

        var success = 0
        paths.forEach { path ->
            if (downloadToGallery(context, path, folder)) success++
        }
        Pair(success, paths.size)
    }
}