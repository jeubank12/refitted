package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.room.RoomCacheExerciseRepository
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
        repositoryImpl: RoomCacheExerciseRepository
    ): ExerciseRepository
}