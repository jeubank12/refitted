package com.litus_animae.refitted.module

import android.content.Context
import com.litus_animae.refitted.data.room.RefittedRoom
import com.litus_animae.refitted.data.room.RefittedRoomProvider
import com.litus_animae.refitted.data.room.RefittedRoomProviderLive
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RefittedRoomModule {

  @Binds
  @Singleton
  abstract fun bindRoomProvider(provider: RefittedRoomProviderLive): RefittedRoomProvider
}