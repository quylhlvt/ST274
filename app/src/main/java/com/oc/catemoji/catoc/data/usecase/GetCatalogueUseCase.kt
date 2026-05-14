package com.oc.catemoji.catoc.data.usecase

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.oc.catemoji.catoc.data.datalocal.api.ApiResult
import com.oc.catemoji.catoc.data.datalocal.api.RemoteDataSource
import com.oc.catemoji.catoc.data.datalocal.manager.AppDataManager
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetCatalogueUseCase @Inject constructor(
    private val remoteDataSource: RemoteDataSource,
    private val appDataManager: AppDataManager,
    @ApplicationContext private val context: Context
) {
    companion object { private const val TAG = "GetCatalogueUseCase" }

    suspend operator fun invoke(): Result<List<CustomModel>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🌐 Fetching catalogue...")
            when (val result = remoteDataSource.fetchTemplates()) {
                is ApiResult.Success -> {
                    Log.d(TAG, "✅ API ${result.data.size} templates")
                    appDataManager.prependOnlineTemplates(result.data)

                    // Preload avatar SONG SONG (parallel) — xong hết rồi mới return
                    // SplashFragment.waitForApiOrTimeout() đang chờ Flow emit,
                    // nên avatars sẽ được cache trước khi user vào màn Choose
                    preloadAvatarsParallel(result.data)

                    Result.success(result.data)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "❌ API error: ${result.message}")
                    Result.failure(Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Preload tất cả avatar song song (coroutineScope + async).
     * Hàm này SUSPEND — caller phải chờ cho đến khi tất cả avatar được cache.
     * → Đảm bảo khi SplashFragment navigate sang ChoosePony, ảnh đã sẵn sàng.
     */
    private suspend fun preloadAvatarsParallel(templates: List<CustomModel>) =
        withContext(Dispatchers.IO) {
            coroutineScope {
                templates
                    .filter { it.avatar.isNotEmpty() }
                    .map { model ->
                        async {
                            try {
                                Glide.with(context)
                                    .load(model.avatar)
                                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                    .centerCrop()
                                    .priority(Priority.HIGH)
                                    .submit()       // submit() blocking — đợi cache xong
                                    .get()
                            } catch (e: Exception) {
                                // Ignore lỗi từng ảnh, không dừng cả batch
                            }
                        }
                    }
                    .awaitAll()
            }
            Log.d(TAG, "✅ Preloaded ${templates.size} avatars vào cache")
        }
}
