package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.RoomSetRecordSink
import com.litus_animae.refitted.data.device.SetRecordSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SetRecordSinkModule {

    @Binds
    @Singleton
    abstract fun bindSetRecordSink(
        setRecordSinkImpl: RoomSetRecordSink
    ): SetRecordSink
}
