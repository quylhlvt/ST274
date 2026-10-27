package com.oc.catemoji.catoc.data.datalocal.di

import android.content.Context
import androidx.room.Room
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bow_wow_battle.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideUserDao(database: AppDatabase) = database.userDao()

    @Provides
    fun provideDogDao(database: AppDatabase) = database.dogDao()

    @Provides
    fun provideHistoryDao(database: AppDatabase) = database.historyDao()
}
