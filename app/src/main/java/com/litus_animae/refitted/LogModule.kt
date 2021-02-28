package com.litus_animae.refitted

import com.litus_animae.refitted.util.AndroidLogUtil
import com.litus_animae.refitted.util.LogUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object LogModule {

    @Provides
    fun bindLogUtil(): LogUtil{
        return AndroidLogUtil
    }
}