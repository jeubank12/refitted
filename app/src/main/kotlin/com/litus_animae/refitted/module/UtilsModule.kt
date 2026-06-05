package com.litus_animae.refitted.module

import com.litus_animae.refitted.util.AndroidLogUtil
import com.litus_animae.refitted.util.LogUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UtilsModule {
    @Provides
    fun provideLogUtil(): LogUtil = AndroidLogUtil
}
