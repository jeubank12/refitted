package com.litus_animae.refitted

import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.room.RoomDynamoExerciseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindRepository(
            repositoryImpl: RoomDynamoExerciseRepository
    ): ExerciseRepository
}