package com.oc.catemoji.catoc.data.datalocal.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterImageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CharacterImageManager"
        private const val IMAGES_DIR = "character_images"
    }

    private val imagesDir: File by lazy {
        File(context.filesDir, IMAGES_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created images directory: $absolutePath")
            }
        }
    }

    /**
     * Lưu bitmap thành file PNG
     * @return Absolute path của file đã lưu
     */
    fun saveBitmap(bitmap: Bitmap, characterId: String): String? {
        return try {
            val filename = "char_${characterId}_${System.currentTimeMillis()}.png"
            val file = File(imagesDir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Log.d(TAG, "✅ Image saved: ${file.absolutePath}")
            file.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Load bitmap từ file path
     */
    fun loadBitmap(imagePath: String): Bitmap? {
        return try {
            if (imagePath.isEmpty()) return null

            val file = File(imagePath)
            if (!file.exists()) {
                Log.w(TAG, "Image file not found: $imagePath")
                return null
            }

            BitmapFactory.decodeFile(imagePath)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Xóa file ảnh
     */
    fun deleteImage(imagePath: String): Boolean {
        return try {
            if (imagePath.isEmpty()) return false

            val file = File(imagePath)
            val deleted = file.delete()

            if (deleted) {
                Log.d(TAG, "✅ Image deleted: $imagePath")
            } else {
                Log.w(TAG, "⚠️ Failed to delete image: $imagePath")
            }

            deleted

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting image: ${e.message}", e)
            false
        }
    }

    /**
     * Xóa ảnh cũ của character trước khi save ảnh mới
     */
    fun deleteOldImage(characterId: String) {
        try {
            imagesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("char_$characterId")) {
                    file.delete()
                    Log.d(TAG, "🗑️ Deleted old image: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting old images: ${e.message}", e)
        }
    }

    /**
     * Cleanup: Xóa tất cả ảnh của các character không còn tồn tại
     */
    fun cleanupOrphanedImages(existingCharacterIds: List<String>) {
        try {
            imagesDir.listFiles()?.forEach { file ->
                val shouldDelete = existingCharacterIds.none { id ->
                    file.name.startsWith("char_$id")
                }

                if (shouldDelete) {
                    file.delete()
                    Log.d(TAG, "🗑️ Cleaned orphaned image: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning orphaned images: ${e.message}", e)
        }
    }

    /**
     * Lấy tổng dung lượng ảnh đã lưu
     */
    fun getTotalStorageSize(): Long {
        return try {
            imagesDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}