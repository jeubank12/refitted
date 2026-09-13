package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.device.WatchService
import com.litus_animae.refitted.garmin.GarminWatchService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchServiceModule {

    @Binds
    @Singleton
    abstract fun bindWatchService(
        watchServiceImpl: GarminWatchService
    ): WatchService
}
