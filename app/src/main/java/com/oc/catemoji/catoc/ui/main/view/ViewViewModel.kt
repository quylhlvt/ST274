package com.oc.catemoji.catoc.ui.main.view

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.core.helper.DownloadHelper
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject



// ViewViewModel.kt
@HiltViewModel
class ViewViewModel @Inject constructor(  private val appDataManager: AppDataManager) : ViewModel() {

    fun downloadFile(context: Context, path: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = DownloadHelper.downloadToGallery(context, path)
            onResult(success)
        }
    }

    fun deleteFile(
        path: String,
        isAvatar: Boolean,
        idEdit: String = "",
        onDone: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // ✅ Nếu là avatar, xóa khỏi customizedCharacters
                if (isAvatar && idEdit.isNotEmpty()) {
                    appDataManager.deleteCustomizedCharacter(idEdit)
                }
                // ✅ Nếu là design, xóa khỏi myDesignPaths
                if (!isAvatar) {
                    val current = appDataManager.myDesignPaths.value.toMutableList()
                    current.remove(path)
                    appDataManager.saveMyDesignToJson(current)
                }
                // Xóa file vật lý
                File(path).delete()
            }
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}