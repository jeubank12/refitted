package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.dynamo.DynamoExerciseSetNetworkService
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ExerciseSetNetworkServiceModule {

    @Binds
    abstract fun bindRepository(
        repositoryImpl: DynamoExerciseSetNetworkService
    ): ExerciseSetNetworkService
}