package com.oc.catemoji.catoc

import android.content.Context
import android.app.ActivityManager
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.LayerTransform
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import com.oc.catemoji.catoc.data.model.studio.StudioCatalogueMapper
import com.oc.catemoji.catoc.data.usecase.GetCatalogueUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
@HiltViewModel
class ViewModelActivity @Inject constructor(
    private val getCatalogueUseCase: GetCatalogueUseCase,
    val appDataManager: AppDataManager,
    private val networkFlow: Flow<Boolean>,
    @ApplicationContext private val context: Context
) :
    ViewModel() {

    // ── EXPOSED FLOWS ─────────────────────────────────────────────────────────

    val characters:           StateFlow<List<CustomModel>> = appDataManager.characters
    val templates:            StateFlow<List<CustomModel>> = appDataManager.templates
    val customizedCharacters: StateFlow<List<CustomModel>> = appDataManager.customizedCharacters
    val backgrounds:          StateFlow<List<String>>      = appDataManager.backgrounds
    val backgroundTexts:      StateFlow<List<String>>      = appDataManager.backgroundTexts
    val stickers:             StateFlow<List<String>>      = appDataManager.stickers
    val speechs:              StateFlow<List<String>>      = appDataManager.speechs
    val myDesignPaths:        StateFlow<List<String>>      = appDataManager.myDesignPaths
    val isLoading:            StateFlow<Boolean>           = appDataManager.isLoading
    val error:                StateFlow<String?>           = appDataManager.error
    private val _bgStickerFailed = MutableStateFlow(false)
    val bgStickerFailed: StateFlow<Boolean> = _bgStickerFailed.asStateFlow()
    private val _bgStickerReady = MutableStateFlow(false)
    val bgStickerReady: StateFlow<Boolean> = _bgStickerReady.asStateFlow()
    private val _bgLoading = MutableStateFlow(false)
    private val _studioCatalogue = MutableStateFlow<List<BodyPartModel>>(emptyList())
    val studioCatalogue: StateFlow<List<BodyPartModel>> = _studioCatalogue.asStateFlow()
    private val _studioLoading = MutableStateFlow(false)
    private val _studioFailed = MutableStateFlow(false)
    private var studioRemoteLoaded = false
    var bgQuantity: Int = 0
    var stickerQuantity: Int = 0
    val bgBaseUrl = "https://lvtglobal.tech/public/app/ST229_CatAvatarCatMaker/bg"

    var cosplayBitmap: Bitmap? = null
    var userResultBitmap: Bitmap? = null
    var customizeBitmap: Bitmap? = null
    var cosplayPercent: Int = 0
    var shouldRestartShow = false
    val networkOnline: StateFlow<Boolean> = networkFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly, // ← Eagerly để không miss network event
            initialValue = false
        )

    // Guard chống gọi fetch trùng từ nhiều màn hình.
    private val _isFetchingOnline = MutableStateFlow(false)
    val isFetchingOnlineFlow: StateFlow<Boolean> = _isFetchingOnline.asStateFlow()
    private val _hasFetchedOnlineCatalogue = MutableStateFlow(false)
    private val _imagesReady = MutableStateFlow(false)
    val imagesReady: StateFlow<Boolean> = _imagesReady.asStateFlow()
    private val _localDataReady = MutableStateFlow(false)
    val localDataReady: StateFlow<Boolean> = _localDataReady.asStateFlow()

    fun notifyImagesReady() {
        _imagesReady.value = true
    }
    // ── INIT ──────────────────────────────────────────────────────────────────

    init {
        Log.d("PERF2", "ViewModelActivity created: ${System.currentTimeMillis()}")
        loadInitialData()
    }
    private suspend fun loadUrlList(baseUrl: String, ext: String): List<String> {
        val results = java.util.Collections.synchronizedMap(mutableMapOf<Int, String>())
        var shouldStop = false
        var start = 1

        while (!shouldStop) {
            val end = start + 9
            coroutineScope {
                (start..end).map { i ->
                    async(Dispatchers.IO) {
                        if (shouldStop) return@async
                        val url = "$baseUrl$i.$ext"
                        try {
                            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "HEAD"
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            val code = connection.responseCode
                            connection.disconnect()
                            if (code == 403 || code == 404) shouldStop = true
                            else results[i] = url
                        } catch (e: Exception) {
                            shouldStop = true
                        }
                    }
                }.awaitAll()
            }
            start += 10
        }
        return results.toSortedMap().values.toList()
    }
    // ✅ Fix — gọi đúng hàm đã có
    fun preloadBackgroundsAndStickers() {
        if (_bgStickerReady.value) return
        loadBackgroundsAndStickers() // ← hàm này đã có trong ViewModelActivity
    }

    fun loadBackgroundsAndStickers() {
        if (_bgLoading.value) return
        if (_bgStickerReady.value && !_bgStickerFailed.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _bgLoading.value = true
            _bgStickerReady.value = false
            try {
                val json = java.net.URL("$bgBaseUrl/bg.json").readText()
                val jsonObj = org.json.JSONObject(json)
                bgQuantity = jsonObj.getJSONArray("Background").getJSONObject(0).getInt("quantity") // ✅ update biến
                stickerQuantity = jsonObj.getJSONArray("Sticker").getJSONObject(0).getInt("quantity") // ✅ update biến
                _bgStickerFailed.value = false
                _bgStickerReady.value = true // Màn hình đang mở sẽ cập nhật adapter.
            } catch (e: Exception) {
                Log.e("ViewModelActivity", "❌ ${e.message}")
                _bgStickerFailed.value = true
            } finally {
                _bgLoading.value = false
            }
        }
    }

    fun loadStudioCatalogue() {
        if (_studioLoading.value || studioRemoteLoaded) return

        viewModelScope.launch(Dispatchers.IO) {
            _studioLoading.value = true
            try {
                if (_studioCatalogue.value.isEmpty()) {
                    val cachedJson = studioPreferences.getString(KEY_STUDIO_JSON, null)
                    if (!cachedJson.isNullOrBlank()) {
                        runCatching {
                            StudioCatalogueMapper.mapBodyParts(cachedJson)
                        }.onSuccess { cachedParts ->
                            if (cachedParts.isNotEmpty()) {
                                _studioCatalogue.value = cachedParts
                                prefetchStudioThumbnails(cachedParts)
                            }
                        }.onFailure {
                            studioPreferences.edit().remove(KEY_STUDIO_JSON).apply()
                        }
                    }
                }

                val connection = URL(StudioCatalogueMapper.JSON_URL)
                    .openConnection() as HttpURLConnection
                val json = try {
                    connection.connectTimeout = STUDIO_TIMEOUT_MS
                    connection.readTimeout = STUDIO_TIMEOUT_MS
                    connection.inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                } finally {
                    connection.disconnect()
                }

                val parts = StudioCatalogueMapper.mapBodyParts(json)
                require(parts.isNotEmpty()) { "Studio catalogue is empty" }
                studioPreferences.edit().putString(KEY_STUDIO_JSON, json).apply()
                _studioCatalogue.value = parts
                _studioFailed.value = false
                studioRemoteLoaded = true
                prefetchStudioThumbnails(parts)
                Log.d("ViewModelActivity", "✅ Studio loaded: ${parts.size}")
            } catch (e: Exception) {
                _studioFailed.value = true
                Log.e("ViewModelActivity", "❌ Studio error: ${e.message}", e)
            } finally {
                _studioLoading.value = false
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // ✅ Bước 1: Load cache templates TRƯỚC (synchronous trên IO)
                val hasCache = withContext(Dispatchers.IO) {
                    appDataManager.loadQuickData()
                }

                if (!hasCache) {
                    appDataManager.loadInitialData()
                }

                _localDataReady.value = true

                Log.d("ViewModelActivity", "📦 After quick load — customized: ${appDataManager.customizedCharacters.value.size}")

                // Ba nguồn online chạy song song, không chờ nguồn trước hoàn thành.
                launch(Dispatchers.IO) { fetchOnlineTemplatesInternal() }
                loadBackgroundsAndStickers()
                loadStudioCatalogue()
                launch { observeNetworkForRetry() }
            } catch (e: Exception) {
                Log.e("ViewModelActivity", "❌ Init error: ${e.message}", e)
                _localDataReady.value = true
            }
        }
    }
    // ── FETCH (duy nhất 1 hàm, có guard) ─────────────────────────────────────

    /**
     * Gọi từ mọi nơi đều an toàn — guard đảm bảo chỉ 1 request chạy tại 1 thời điểm.
     * BaseActivity gọi khi vào màn + có mạng + chưa có online data.
     */
    private suspend fun observeNetworkForRetry() {
        networkOnline.collect { isOnline ->
            if (isOnline && _bgStickerFailed.value && !_bgLoading.value) {
                Log.d("ViewModelActivity", "🔄 Network restored, retrying...")
                _bgStickerFailed.value = false
                loadBackgroundsAndStickers()
            }
            if (isOnline && _studioFailed.value && !_studioLoading.value) {
                _studioFailed.value = false
                loadStudioCatalogue()
            }
        }
    }
    private suspend fun fetchOnlineTemplatesInternal() {
        if (_isFetchingOnline.value) {
            Log.d("ViewModelActivity", "⏭️ Already fetching, skip")
            return
        }
        _isFetchingOnline.value = true
        try {
            Log.d("ViewModelActivity", "📡 fetchOnlineTemplates start")
            val result = getCatalogueUseCase()
            if (result.isSuccess) {
                val newTemplates = result.getOrNull() ?: return
                _hasFetchedOnlineCatalogue.value = true
                appDataManager.saveApiCache(newTemplates)
                appDataManager.mergeApiTemplates(newTemplates)
                prefetchTemplateImages(newTemplates)
                Log.d("ViewModelActivity", "✅ Online templates loaded: ${newTemplates.size}")
            } else {
                Log.e("ViewModelActivity", "❌ API failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e("ViewModelActivity", "❌ fetchOnlineTemplates error: ${e.message}", e)
        } finally {
            _isFetchingOnline.value = false
        }
    }

    /** Public — BaseActivity và pull-to-refresh gọi */
    fun fetchOnlineTemplates() {
        viewModelScope.launch(Dispatchers.IO){ fetchOnlineTemplatesInternal() }
    }

    fun hasCompleteOnlineCatalogue(): Boolean =
        !_isFetchingOnline.value &&
            (_hasFetchedOnlineCatalogue.value || templates.value.any { it.id.startsWith("online_") })

    fun forceReloadAll() {
        viewModelScope.launch {
            _localDataReady.value = false
            try {
                appDataManager.forceReloadAll()
            } finally {
                _localDataReady.value = true
            }
        }
    }

    fun refreshApiData() {
        viewModelScope.launch { appDataManager.refreshFromApi() }
    }

    // ── PREFETCH IMAGES ───────────────────────────────────────────────────────

    private suspend fun prefetchTemplateImages(templates: List<CustomModel>) {
        withContext(Dispatchers.IO) {
            templates.take(5).forEach { template ->
                template.listPath.forEach { bp ->
                    val firstColor = bp.listPath.firstOrNull() ?: return@forEach
                    val firstPath  = firstColor.listPath
                        .firstOrNull { it != "none" && it != "dice" }
                        ?: return@forEach
                    runCatching {
                        Glide.with(context)
                            .asBitmap()
                            .load(firstPath)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .override(256, 256)
                            .preload()
                    }
                }
            }
        }
    }

    private fun prefetchStudioThumbnails(parts: List<BodyPartModel>) {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (activityManager.isLowRamDevice) return

        parts.forEach { bodyPart ->
            bodyPart.listThumbPath
                .take(STUDIO_VISIBLE_THUMB_COUNT)
                .forEach { path ->
                    Glide.with(context)
                        .load(path)
                        .priority(Priority.LOW)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(true)
                        .override(STUDIO_THUMB_SIZE_PX, STUDIO_THUMB_SIZE_PX)
                        .preload()
                }
        }
    }

    // ── QUERIES ───────────────────────────────────────────────────────────────

    fun getCharacterByIndex(index: Int): CustomModel? = appDataManager.getCharacterByIndex(index)
    fun getCharacterById(id: String): CustomModel?    = appDataManager.getCharacterById(id)
    fun isTemplate(id: String): Boolean               = appDataManager.isTemplate(id)
    fun getTemplateIndexByAvt(avt: String): Int       = appDataManager.getTemplateIndexByAvt(avt)
    fun getCharacterIndexById(id: String): Int        = characters.value.indexOfFirst { it.id == id }

    fun getTemplateIndexForCustomized(customizedId: String): Int {
        val customized = customizedCharacters.value.firstOrNull { it.id == customizedId }
            ?: return -1

        // ✅ Ưu tiên templateId
        val byTemplateId = customized.templateId?.let { tplId ->
            templates.value.indexOfFirst { it.id == tplId }.takeIf { it >= 0 }
        }
        if (byTemplateId != null) return byTemplateId

        // ✅ Fallback avatar
        val byAvatar = templates.value.indexOfFirst { it.avatar == customized.avatar }
            .takeIf { it >= 0 }
        if (byAvatar != null) return byAvatar

        Log.w("ViewModelActivity", "⚠️ Template not found for customizedId=$customizedId, templateId=${customized.templateId}")
        return -1
    }

    /** Validate saved indices against the current template before opening Edit. */
    fun hasValidSelectionsForCustomized(customizedId: String): Boolean {
        val customized = customizedCharacters.value.firstOrNull { it.id == customizedId }
            ?: return false
        val templateIndex = getTemplateIndexForCustomized(customizedId)
        val template = templates.value.getOrNull(templateIndex) ?: return false
        return customized.hasValidSelectionsFor(template)
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun saveCharacterWithSelections(
        character:  CustomModel,
        selections: List<SelectionIndex>,
        imageSave:  String  = "",
        isFlipped:  Boolean = false,
        layerTransforms: Map<Int, LayerTransform> = emptyMap()
    ) {
        viewModelScope.launch {
            val toSave = if (isTemplate(character.id)) {
                character.copy(
                    id         = UUID.randomUUID().toString(),
                    templateId = character.id,
                    selections = ArrayList(selections),
                    imageSave  = imageSave,
                    isFlipped  = isFlipped,
                    layerTransforms = layerTransforms,
                    createdAt  = System.currentTimeMillis(),
                    updatedAt  = System.currentTimeMillis()
                    // ✅ KHÔNG set listPath — giữ nguyên từ template
                )
            } else {
                character.copy(
                    selections = ArrayList(selections),
                    imageSave  = imageSave,
                    isFlipped  = isFlipped,
                    layerTransforms = layerTransforms,
                    updatedAt  = System.currentTimeMillis()
                    // ✅ KHÔNG set listPath
                )
            }
            Log.d("ViewModelActivity", "💾 Saving: id=${toSave.id}, templateId=${toSave.templateId}, imageSave=${toSave.imageSave}")
            appDataManager.updateCustomizedCharacter(toSave)
        }
    }

    fun deleteCharacter(characterId: String) {
        viewModelScope.launch {
            if (isTemplate(characterId)) return@launch
            appDataManager.deleteCustomizedCharacter(characterId)
        }
    }

    // ── SELECTION HELPERS ─────────────────────────────────────────────────────

    fun resolvePath(character: CustomModel, sel: SelectionIndex): String? =
        appDataManager.resolvePathFromSelection(character, sel)

    fun resolveAllPaths(character: CustomModel, selections: List<SelectionIndex>): List<Pair<Int, String>> =
        appDataManager.resolveAllPaths(character, selections)

    // ── MY DESIGNS ────────────────────────────────────────────────────────────

    fun addMyDesign(path: String)    { viewModelScope.launch { appDataManager.addMyDesignPath(path) } }
    fun removeMyDesign(path: String) { viewModelScope.launch { appDataManager.removeMyDesignPath(path) } }

    // ── CLEAR ─────────────────────────────────────────────────────────────────

    fun clearData() { appDataManager.clearData() }

    companion object {
        private const val STUDIO_PREFS = "studio_catalogue_cache"
        private const val KEY_STUDIO_JSON = "studio_json"
        private const val STUDIO_TIMEOUT_MS = 15_000
        private const val STUDIO_VISIBLE_THUMB_COUNT = 5
        private const val STUDIO_THUMB_SIZE_PX = 192
    }

    private val studioPreferences by lazy {
        context.getSharedPreferences(STUDIO_PREFS, Context.MODE_PRIVATE)
    }
}
