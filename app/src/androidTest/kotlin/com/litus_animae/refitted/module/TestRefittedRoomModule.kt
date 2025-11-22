package com.litus_animae.refitted.module

import android.content.Context
import androidx.room.Room
import com.litus_animae.refitted.room.RefittedRoom
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.util.LogUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test Hilt module that provides an in-memory Room database for instrumented tests.
 * This module replaces the production RefittedRoomModule.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RefittedRoomModule::class]
)
object TestRefittedRoomModule {

    @Provides
    @Singleton
    fun provideInMemoryDatabase(
        @ApplicationContext context: Context,
        log: LogUtil
    ): RefittedRoomProvider {
        return object : RefittedRoomProvider {
            override val refittedRoom: RefittedRoom by lazy {
                log.i("TestRefittedRoomModule", "Building in-memory Room database for tests")
                Room.inMemoryDatabaseBuilder(
                    context,
                    RefittedRoom::class.java
                )
                    .allowMainThreadQueries() // For tests, we allow main thread queries
                    .build()
            }
        }
    }
}
