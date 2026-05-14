package com.oc.catemoji.catoc.data.datalocal.api

// com/example/basefragment/data/datalocal/api/ApiHelper.kt

import com.facebook.shimmer.BuildConfig
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiHelper @Inject constructor() {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)   // ← giống 226
        .readTimeout(6, TimeUnit.SECONDS)      // ← giống 226
        .writeTimeout(6, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        })
        .build()
    val api1: AvatarApiService = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL_1)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AvatarApiService::class.java)

    val api2: AvatarApiService = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL_2)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AvatarApiService::class.java)
}