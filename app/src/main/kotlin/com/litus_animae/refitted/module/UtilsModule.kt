package com.litus_animae.refitted.module

import com.litus_animae.refitted.util.UtilModule
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module that includes UtilModule (plain Dagger module) into the Hilt component graph.
 * This makes LogUtil available for dependency injection throughout the app.
 */
@Module(includes = [UtilModule::class])
@InstallIn(SingletonComponent::class)
object UtilsModule
