package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.SavedStateRepository
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.data.room.RoomCacheWorkoutPlanRepository
import com.litus_animae.refitted.data.room.RoomSavedStateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class SavedStateRepositoryModule {

    @Binds
    abstract fun bindRepository(
        repositoryImpl: RoomSavedStateRepository
    ): SavedStateRepository
}