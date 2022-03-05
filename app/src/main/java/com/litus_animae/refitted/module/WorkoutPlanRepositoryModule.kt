package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.data.room.RoomCacheWorkoutPlanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class WorkoutPlanRepositoryModule {

    @Binds
    abstract fun bindRepository(
        repositoryImpl: RoomCacheWorkoutPlanRepository
    ): WorkoutPlanRepository
}