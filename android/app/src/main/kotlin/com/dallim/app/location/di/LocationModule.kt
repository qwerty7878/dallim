package com.dallim.app.location.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.dallim.app.location.GpsPointDao
import com.dallim.app.location.RunLocalDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Room(로컬 GPS 저장소) + WorkManager 인스턴스 DI — S-20~S-24 GPS 파이프라인. */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideRunLocalDatabase(@ApplicationContext context: Context): RunLocalDatabase =
        Room.databaseBuilder(context, RunLocalDatabase::class.java, "dallim_run_local.db").build()

    @Provides
    fun provideGpsPointDao(database: RunLocalDatabase): GpsPointDao = database.gpsPointDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
