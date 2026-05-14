package com.oc.catemoji.catoc.data.datalocal.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.oc.catemoji.catoc.core.helper.NetworkMonitor
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DataLocalModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(application: Application): SharedPreferences =
        application.getSharedPreferences("DEFAULT", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideSharedPreferencesEditor(sharedPreferences: SharedPreferences): SharedPreferences.Editor =
        sharedPreferences.edit()

    @Provides
    @Singleton
    fun provideSharedPreferencesManager(
        sharedPreferences: SharedPreferences,
        editor: SharedPreferences.Editor
    ): SharedPreferencesManager =
        SharedPreferencesManager.apply {
            this.sharedPreferences = sharedPreferences
            this.editor = editor
        }
    @Singleton
    @Provides
    fun provideNetworkMonitor(
        @ApplicationContext context: Context
    ): NetworkMonitor = NetworkMonitor(context)

    @Provides
    fun provideNetworkFlow(
        networkMonitor: NetworkMonitor
    ): Flow<Boolean> = networkMonitor.isOnline
}