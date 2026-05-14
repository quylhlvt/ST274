package com.oc.catemoji.catoc.data.repository

import com.oc.catemoji.catoc.data.datalocal.api.CatalogueApi
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Repository chịu trách nhiệm gọi API liên quan đến CustomModel (characters)
 */
@Singleton
class ApiRepository @Inject constructor(
    private val catalogueApi: CatalogueApi) {
    suspend fun getCatalogue() = withContext(Dispatchers.IO) {
        catalogueApi.getData()
    }
}
