package com.litus_animae.refitted.util

import dagger.Module
import dagger.Provides

@Module
object UtilModule {

    @Provides
    fun provideLogUtil(): LogUtil {
        return AndroidLogUtil
    }
}
