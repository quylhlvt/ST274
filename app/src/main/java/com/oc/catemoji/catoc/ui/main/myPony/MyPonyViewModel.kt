package com.oc.catemoji.catoc.ui.main.myPony

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.core.helper.DownloadHelper
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import com.oc.catemoji.catoc.data.model.mypony.MyAlbumModel
import com.oc.catemoji.catoc.utils.share.telegram.TelegramSharing
import com.oc.catemoji.catoc.utils.share.whatsapp.IdGenerator
import com.oc.catemoji.catoc.utils.share.whatsapp.StickerBook
import com.oc.catemoji.catoc.utils.share.whatsapp.StickerPack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MyPonyViewModel @Inject constructor(
    private val appDataManager: AppDataManager
) : ViewModel() {

    companion object {
        private const val TAG = "MyPonyViewModel"
    }

    // Avatar list
    private val _myAvatarList = MutableStateFlow<List<MyAlbumModel>>(emptyList())
    val myAvatarList: StateFlow<List<MyAlbumModel>> = _myAvatarList.asStateFlow()

    // Design list
    private val _myDesignList = MutableStateFlow<List<MyAlbumModel>>(emptyList())
    val myDesignList: StateFlow<List<MyAlbumModel>> = _myDesignList.asStateFlow()

    // Download state
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.IDLE)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    enum class DownloadState {
        IDLE, LOADING, SUCCESS, ERROR
    }

    // ==================== LOAD AVATAR DATA ====================

    /**
     * ✅ Load avatars TỪ customized characters (KHÔNG scan storage folder)
     */
    fun loadMyAvatar(context: Context, forceReload: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Loading avatars from customized characters...")

                // ✅ TRỰC TIẾP lấy từ appDataManager.customizedCharacters
                val customized = appDataManager.customizedCharacters.value

                Log.d(TAG, "📦 Found ${customized.size} customized characters")

                // ✅ Filter: chỉ lấy characters có ảnh và file tồn tại
                val avatarList = customized
                    .filter { it.imageSave.isNotEmpty() && File(it.imageSave).exists() }
                    .sortedByDescending { it.updatedAt }  // Mới nhất lên đầu
                    .map { character ->
                        MyAlbumModel(
                            path = character.imageSave,
                            isSelected = false,
                            isShowSelection = false,
                            type = 1,
                            idEdit = character.id
                        )
                    }

                withContext(Dispatchers.Main) {
                    _myAvatarList.value = avatarList
                    Log.d(TAG, "✅ Avatar list updated: ${avatarList.size} items")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading avatars: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _myAvatarList.value = emptyList()
                }
            }
        }
    }

    // ==================== LOAD DESIGN DATA ====================

    fun loadMyDesign(context: Context, forceReload: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Loading designs...")

                val paths = appDataManager.myDesignPaths.value

                Log.d(TAG, "📦 Raw paths from AppDataManager: ${paths.size}")

                // Filter only existing files
                val existingPaths = paths.filter { path ->
                    val exists = File(path).exists()
                    if (!exists) {
                        Log.w(TAG, "⚠️ File not found: $path")
                    }
                    exists
                }

                Log.d(TAG, "✅ Existing paths: ${existingPaths.size}")

                val list = existingPaths.map { path ->
                    MyAlbumModel(
                        path = path,
                        isSelected = false,
                        isShowSelection = false,
                        idEdit = "",
                        type = 2
                    )
                }

                withContext(Dispatchers.Main) {
                    _myDesignList.value = list
                    Log.d(TAG, "✅ Design list updated: ${list.size} items")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading designs: ${e.message}", e)
            }
        }
    }

    // ==================== SELECTION MANAGEMENT ====================

    fun showLongClick(position: Int, isAvatar: Boolean) {
        val currentList = if (isAvatar) _myAvatarList.value else _myDesignList.value
        val updatedList = currentList.mapIndexed { index, item ->
            if (index == position) {
                item.copy(isSelected = true, isShowSelection = true)
            } else {
                item.copy(isShowSelection = true)
            }
        }

        if (isAvatar) {
            _myAvatarList.value = updatedList
        } else {
            _myDesignList.value = updatedList
        }
    }

    fun toggleSelect(position: Int, isAvatar: Boolean = false) {
        val currentList = if (isAvatar) _myAvatarList.value else _myDesignList.value
        val updatedList = currentList.mapIndexed { index, item ->
            if (index == position) {
                item.copy(isSelected = !item.isSelected)
            } else {
                item
            }
        }

        if (isAvatar) {
            _myAvatarList.value = updatedList
        } else {
            _myDesignList.value = updatedList
        }
    }

    fun selectAll(shouldSelectAll: Boolean, isAvatar: Boolean = true) {
        val currentList = if (isAvatar) _myAvatarList.value else _myDesignList.value
        val updatedList = currentList.map { item ->
            item.copy(
                isSelected = shouldSelectAll,
                isShowSelection = shouldSelectAll || item.isShowSelection
            )
        }

        if (isAvatar) {
            _myAvatarList.value = updatedList
        } else {
            _myDesignList.value = updatedList
        }
    }

    fun getPathSelected(isAvatar: Boolean = true): ArrayList<String> {
        val currentList = if (isAvatar) _myAvatarList.value else _myDesignList.value
        return ArrayList(currentList.filter { it.isSelected }.map { it.path })
    }

    // ==================== DELETE OPERATIONS ====================

    /**
     * ✅ Delete avatar - Xóa từ customized characters
     */
    // MyPonyViewModel.kt
    fun deleteItem(context: Context, paths: ArrayList<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                paths.forEach { imagePath ->
                    val character = appDataManager.customizedCharacters.value
                        .find { it.imageSave == imagePath }

                    if (character != null) {
                        // ✅ Xóa khỏi AppDataManager — tự động trigger StateFlow
                        appDataManager.deleteCustomizedCharacter(character.id)
                        deleteFileFromStorage(context, imagePath)
                    }
                }
                // ✅ KHÔNG cần gọi loadMyAvatar() nữa
                // vì observeData() đang collect viewModelActivity.customizedCharacters
                // và appDataManager.customizedCharacters là cùng source
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting items: ${e.message}", e)
            }
        }
    }
    /**
     * ✅ Delete design
     */
    fun deleteItemDesign(paths: ArrayList<String>, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ Deleting ${paths.size} design items")

                paths.forEach { path ->
                    deleteFileFromStorage(context, path)
                }

                // ✅ Update AppDataManager
                val current = appDataManager.myDesignPaths.value.toMutableList()
                val sizeBefore = current.size
                current.removeAll(paths)

                Log.d(TAG, "📦 Removed ${sizeBefore - current.size} paths from AppDataManager")

                appDataManager.saveMyDesignToJson(current)

                withContext(Dispatchers.Main) {
                    loadMyDesign(context, true)
                }

                Log.d(TAG, "✅ Delete design completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting designs: ${e.message}", e)
            }
        }
    }

    private suspend fun deleteFileFromStorage(context: Context, path: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "🗑️ File deleted: $path (success: $deleted)")
                } else {
                    Log.w(TAG, "⚠️ File not found: $path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting file: ${e.message}", e)
            }
        }
    }

    // ==================== DOWNLOAD ====================

    fun downloadFiles(context: Context, paths: ArrayList<String>) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.LOADING
            val (success, total) = DownloadHelper.downloadMultipleToGallery(context, paths)
            _downloadState.value = if (success == total) DownloadState.SUCCESS else DownloadState.ERROR
        }
    }


    // ==================== WHATSAPP INTEGRATION ====================

    fun addToWhatsapp(
        context: Context,
        packageName: String,
        paths: ArrayList<String>,
        callback: (StickerPack?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                StickerBook.init(context)

                // Xóa cache cũ tránh tích lũy
                context.cacheDir.listFiles { f -> f.name.startsWith("sticker_wa_") }
                    ?.forEach { it.delete() }

                val uriList = ArrayList<Uri>()
                paths.forEachIndexed { index, path ->
                    val file = File(path)
                    if (!file.exists()) return@forEachIndexed

                    // Decode với inSampleSize để tiết kiệm RAM
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                    opts.inJustDecodeBounds = false
                    opts.inSampleSize = calculateInSampleSize(opts, 512, 512)

                    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                        ?: return@forEachIndexed
                    val resized = Bitmap.createScaledBitmap(bmp, 512, 512, true)
                    bmp.recycle()

                    // Lưu vào cache với tên cố định theo index
                    val outFile = File(context.cacheDir, "sticker_wa_$index.webp")
                    FileOutputStream(outFile).use { out ->
                        resized.compress(Bitmap.CompressFormat.WEBP, 90, out)
                    }
                    resized.recycle()

                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.provider", outFile
                    )
                    // Grant cho cả 2 phiên bản WhatsApp
                    context.grantUriPermission("com.whatsapp",     uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.grantUriPermission("com.whatsapp.w4b", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    uriList.add(uri)
                }

                if (uriList.size < 3) {
                    withContext(Dispatchers.Main) { callback(null) }
                    return@launch
                }

                // identifier từ packName (stable), KHÔNG dùng paths[0]
                val identifier = IdGenerator.generateIdFromUrl(context, packageName)
                val stickerPack = StickerPack(identifier, packageName, uriList, context)
                StickerBook.addPackIfNotAlreadyAdded(stickerPack)

                withContext(Dispatchers.Main) { callback(stickerPack) }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to WhatsApp: ${e.message}", e)
                withContext(Dispatchers.Main) { callback(null) }
            }
        }
    }

    private fun calculateInSampleSize(opts: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        var size = 1
        if (opts.outHeight > reqH || opts.outWidth > reqW) {
            val halfH = opts.outHeight / 2
            val halfW = opts.outWidth / 2
            while (halfH / size >= reqH && halfW / size >= reqW) size *= 2
        }
        return size
    }

    // ==================== TELEGRAM INTEGRATION ====================

    fun addToTelegram(context: Context, paths: ArrayList<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val telegramPackages = listOf(
                    "org.telegram.messenger",
                    "org.telegram.messenger.web",
                    "org.telegram.plus"
                )

                val uriList = ArrayList<Uri>()

                paths.forEachIndexed { index, path ->
                    val file = File(path)
                    if (!file.exists()) return@forEachIndexed

                    val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                    val resized = Bitmap.createScaledBitmap(bmp, 512, 512, true)
                    bmp.recycle()

                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "tg_sticker_$index.png")
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TelegramStickers")
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val mediaUri = resolver.insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                        ) ?: return@forEachIndexed
                        resolver.openOutputStream(mediaUri)?.use { out ->
                            resized.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        resolver.update(mediaUri, android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        }, null, null)
                        mediaUri
                    } else {
                        val cacheDir = context.externalCacheDir ?: context.cacheDir
                        val outFile = File(cacheDir, "tg_sticker_$index.png")
                        FileOutputStream(outFile).use { out ->
                            resized.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", outFile
                        ).also { uri ->
                            telegramPackages.forEach { pkg ->
                                runCatching {
                                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                        }
                    }

                    resized.recycle()
                    uriList.add(uri)
                    Log.d(TAG, "✅ tg_sticker_$index → $uri")
                }

                if (uriList.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        TelegramSharing.importToTelegram(context, uriList)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to Telegram: ${e.message}", e)
            }
        }
    }
}