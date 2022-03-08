package com.litus_animae.refitted.module

import com.litus_animae.refitted.util.AndroidLogUtil
import com.litus_animae.refitted.util.LogUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(ViewModelComponent::class, SingletonComponent::class)
object LogModule {

    @Provides
    fun bindLogUtil(): LogUtil {
        return AndroidLogUtil
    }
}