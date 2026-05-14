package com.oc.catemoji.catoc

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex
import com.oc.catemoji.catoc.data.usecase.GetCatalogueUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Guard chống gọi fetch trùng từ nhiều fragment
    private val _isFetchingOnline = MutableStateFlow(false)
    val isFetchingOnlineFlow: StateFlow<Boolean> = _isFetchingOnline.asStateFlow()
    private val _imagesReady = MutableStateFlow(false)
    val imagesReady: StateFlow<Boolean> = _imagesReady.asStateFlow()

    fun notifyImagesReady() {
        _imagesReady.value = true
    }
    // ── INIT ──────────────────────────────────────────────────────────────────

    init {
        Log.d("PERF2", "ViewModelActivity created: ${System.currentTimeMillis()}")
        loadInitialData()
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

                Log.d("ViewModelActivity", "📦 After quick load — customized: ${appDataManager.customizedCharacters.value.size}")

                // ✅ Bước 2: Fetch online SAU KHI local đã ổn định
                // Gọi trực tiếp, KHÔNG launch(IO) riêng → tránh race condition
                fetchOnlineTemplatesInternal()

                Log.d("ViewModelActivity", "📦 After online fetch — customized: ${appDataManager.customizedCharacters.value.size}")
            } catch (e: Exception) {
                Log.e("ViewModelActivity", "❌ Init error: ${e.message}", e)
            }
        }
    }
    // ── FETCH (duy nhất 1 hàm, có guard) ─────────────────────────────────────

    /**
     * Gọi từ mọi nơi đều an toàn — guard đảm bảo chỉ 1 request chạy tại 1 thời điểm.
     * BaseFragment gọi khi vào màn + có mạng + chưa có online data.
     */
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

    /** Public — BaseFragment và pull-to-refresh gọi */
    fun fetchOnlineTemplates() {
        viewModelScope.launch(Dispatchers.IO){ fetchOnlineTemplatesInternal() }
    }

    fun forceReloadAll() {
        viewModelScope.launch { appDataManager.forceReloadAll() }
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

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun saveCharacterWithSelections(
        character:  CustomModel,
        selections: List<SelectionIndex>,
        imageSave:  String  = "",
        isFlipped:  Boolean = false
    ) {
        viewModelScope.launch {
            val toSave = if (isTemplate(character.id)) {
                character.copy(
                    id         = UUID.randomUUID().toString(),
                    templateId = character.id,
                    selections = ArrayList(selections),
                    imageSave  = imageSave,
                    isFlipped  = isFlipped,
                    createdAt  = System.currentTimeMillis(),
                    updatedAt  = System.currentTimeMillis()
                    // ✅ KHÔNG set listPath — giữ nguyên từ template
                )
            } else {
                character.copy(
                    selections = ArrayList(selections),
                    imageSave  = imageSave,
                    isFlipped  = isFlipped,
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
}