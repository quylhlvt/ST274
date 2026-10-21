package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.oc.catemoji.catoc.DiyAssetCategory
import com.oc.catemoji.catoc.core.extention.saveToFile
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import com.oc.catemoji.catoc.data.model.customDiy.DiyCatCategory
import com.oc.catemoji.catoc.data.model.customDiy.DiyCatalogue
import com.oc.catemoji.catoc.ui.main.add_character.DesignSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltViewModel
class CustomDiyPetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val appDataManager: AppDataManager
) : ViewModel() {
    private val _catalogue = MutableStateFlow(DiyCatalogue())
    val catalogue = _catalogue.asStateFlow()
    private var catalogueBound = false

    // These bitmaps are independent of Glide's pool and can safely be shared by
    // repeated keys. Eviction drops only the cache reference, never recycles a layer.
    private val bitmapCache = object : LruCache<ImageKey, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: ImageKey, value: Bitmap) = value.allocationByteCount
    }
    private data class ImageKey(val path: String, val trimPadding: Boolean)
    private val imageRequests = mutableMapOf<ImageKey, Deferred<Bitmap>>()
    private val imageSlots = Semaphore(3)

    fun bindCatalogue(
        baseUrl: String,
        cats: StateFlow<List<DiyAssetCategory>>,
        eyes: StateFlow<Int>,
        keyboard: StateFlow<Int>,
        tags: StateFlow<Int>
    ) {
        if (catalogueBound) return
        catalogueBound = true
        publishCatalogue(buildCatalogue(baseUrl, cats.value, eyes.value, keyboard.value, tags.value))
        viewModelScope.launch {
            combine(cats, eyes, keyboard, tags) { categories, eyeCount, keyCount, tagCount ->
                withContext(Dispatchers.Default) {
                    buildCatalogue(baseUrl, categories, eyeCount, keyCount, tagCount)
                }
            }.collect(::publishCatalogue)
        }
    }

    private fun publishCatalogue(value: DiyCatalogue) {
        val selected = (savedStateHandle.get<Int>(SELECTED_CATEGORY) ?: 0)
            .coerceIn(0, value.cats.lastIndex.coerceAtLeast(0))
        _catalogue.value = value.copy(selectedCatCategory = selected)
    }

    fun selectCatCategory(position: Int) {
        if (position !in _catalogue.value.cats.indices) return
        savedStateHandle[SELECTED_CATEGORY] = position
        _catalogue.value = _catalogue.value.copy(selectedCatCategory = position)
    }

    suspend fun loadCanvasImage(path: String, trimPadding: Boolean): Bitmap =
        withContext(Dispatchers.Main.immediate) {
            val key = ImageKey(path, trimPadding)
            bitmapCache.get(key)?.let { return@withContext it }
            // All cache/request bookkeeping is confined to Main. Repeated taps
            // await the same decode, while each still creates its own canvas layer.
            val request = imageRequests.getOrPut(key) {
                viewModelScope.async {
                    try {
                        imageSlots.withPermit {
                            decodeCanvasImage(key).also { bitmapCache.put(key, it) }
                        }
                    } finally {
                        imageRequests.remove(key)
                    }
                }
            }
            request.await()
        }

    private suspend fun decodeCanvasImage(key: ImageKey): Bitmap {
        val manager = Glide.with(context)
        val request = manager.asBitmap()
            .load(key.path)
            .override(768, 768)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .disallowHardwareConfig()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .submit()
        try {
            val bitmap = runInterruptible(Dispatchers.IO) { request.get() }
            return withContext(Dispatchers.Default) {
                val prepared = if (key.trimPadding) DiyBitmapRenderer.trimTransparentPadding(bitmap) else bitmap
                // A copy is required: clearing Glide returns its resource to the pool.
                val owned = prepared.copy(Bitmap.Config.ARGB_8888, false)
                if (prepared !== bitmap) prepared.recycle()
                owned
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { manager.clear(request) }
        }
    }

    suspend fun renderText(text: String, color: Int, textSize: Float = 150f): Bitmap =
        withContext(Dispatchers.Default) { DiyBitmapRenderer.renderText(text, color, textSize) }

    /** Takes ownership of the canvas snapshot and always releases it after saving. */
    suspend fun saveDesign(bitmap: Bitmap): String? = withContext(NonCancellable + Dispatchers.IO) {
        try {
            val path = bitmap.saveToFile(context, "diy_pet") ?: return@withContext null
            appDataManager.addMyDesignPath(path, DesignSource.DIY)
            path
        } finally {
            bitmap.recycle()
        }
    }

    override fun onCleared() {
        bitmapCache.evictAll()
        super.onCleared()
    }

    companion object {
        const val DELETE_KEY = "__delete__"
        val KEY_LABELS = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
        private const val SELECTED_CATEGORY = "diy_selected_cat_category"

        internal fun buildCatalogue(
            baseUrl: String,
            cats: List<DiyAssetCategory>,
            eyes: Int,
            keyboard: Int,
            tags: Int
        ): DiyCatalogue {
            val keys = (1..(keyboard.takeIf { it > 0 } ?: 36).coerceAtMost(36)).map {
                "$baseUrl/Keybroad/Keybroad/$it.png"
            }
            var start = 0
            val rows = KEY_LABELS.mapIndexed { index, labels ->
                val end = (start + labels.length).coerceAtMost(keys.size)
                val row = keys.subList(start, end).toList()
                start = end
                if (index == KEY_LABELS.lastIndex) row + DELETE_KEY else row
            }
            return DiyCatalogue(
                cats = cats.map { category ->
                    DiyCatCategory(category.category.replace('_', ' '), (1..category.quantity).map {
                        "$baseUrl/Cat/${category.category}/$it.png"
                    })
                },
                eyes = (1..(eyes.takeIf { it > 0 } ?: 33)).map { "$baseUrl/Eyes/$it.png" },
                keyboardRows = rows,
                tags = (1..(tags.takeIf { it > 0 } ?: 34)).map {
                    "$baseUrl/Keybroad/Tag/$it.png"
                }
            )
        }
    }
}
