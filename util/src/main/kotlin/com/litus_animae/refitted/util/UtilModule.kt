package com.litus_animae.refitted.util

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    fun provideLogUtil(): LogUtil {
        return AndroidLogUtil
    }
}
