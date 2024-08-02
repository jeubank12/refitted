package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.firebase.AuthProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

  @Provides
  fun provideRoom(
  ): AuthProvider {
    return AuthProvider()
  }
}