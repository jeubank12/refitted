package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.data.room.RoomCacheExerciseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

/**
 * Test module that provides ExerciseRepository at Singleton scope for testing.
 * Replaces the ViewModelComponent-scoped production module.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ExerciseRepositoryModule::class]
)
abstract class TestExerciseRepositoryModule {

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    @Binds
    abstract fun bindRepository(
        repositoryImpl: RoomCacheExerciseRepository
    ): ExerciseRepository
}
