package com.litus_animae.refitted

import android.content.Context
import com.litus_animae.refitted.data.WorkoutPlanNetworkService
import com.litus_animae.refitted.data.dynamo.DynamoWorkoutPlanNetworkService
import com.litus_animae.refitted.data.room.RefittedRoom
import com.litus_animae.refitted.data.room.RoomRefittedDataService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RefittedRoomModule {

    @Provides
    fun provideRoom(
        @ApplicationContext applicationContext: Context
    ): RefittedRoom {
        return RoomRefittedDataService.getRefittedRoom(applicationContext)
    }
}