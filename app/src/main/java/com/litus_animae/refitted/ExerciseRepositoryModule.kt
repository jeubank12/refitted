package com.litus_animae.refitted

import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.room.RoomDynamoExerciseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@Module
@InstallIn(ViewModelComponent::class)
abstract class ExerciseRepositoryModule {

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    @Binds
    abstract fun bindRepository(
            repositoryImpl: RoomDynamoExerciseRepository
    ): ExerciseRepository
}