package com.oc.catemoji.catoc.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class TrainingEffect(
    val folder: Int,
    val path: String,
)

@Singleton
class TrainingAssetRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val assetContext = context.applicationContext

    suspend fun loadEffects(): List<TrainingEffect> = withContext(Dispatchers.IO) {
        (1..3).flatMap { folder ->
            imagePaths("imgEffect/$folder").map { path ->
                TrainingEffect(folder = folder, path = path)
            }
        }
    }

    suspend fun loadBackgrounds(): List<String> = withContext(Dispatchers.IO) {
        imagePaths("bg")
    }

    private fun imagePaths(directory: String): List<String> {
        val files = try {
            assetContext.assets.list(directory).orEmpty()
        } catch (_: IOException) {
            emptyArray()
        }
        return files
            .filter { filename ->
                filename.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS
            }
            .sorted()
            .map { filename -> "file:///android_asset/$directory/$filename" }
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg")
    }
}
