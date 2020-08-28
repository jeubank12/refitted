package com.litus_animae.refitted

import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.room.coroutine.RoomDynamoExerciseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindRepository(
            repositoryImpl: RoomDynamoExerciseRepository
    ): ExerciseRepository
}