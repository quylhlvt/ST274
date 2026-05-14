package com.oc.catemoji.catoc.data.datalocal.api

import com.oc.catemoji.catoc.data.model.api.CharacterResponse
import com.oc.catemoji.catoc.data.model.api.PartAPI
import retrofit2.Response
import retrofit2.http.GET

interface CatalogueApi {
    @GET("api/ST183_PrincessAvatarMaker")
    suspend fun getData(): Response<CharacterResponse>
}