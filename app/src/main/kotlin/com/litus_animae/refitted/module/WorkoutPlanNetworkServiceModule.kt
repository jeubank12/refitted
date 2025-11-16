package com.litus_animae.refitted.module

import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.data.dynamo.DynamoWorkoutPlanNetworkService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutPlanNetworkServiceModule {

    @Binds
    abstract fun bindRepository(
        repositoryImpl: DynamoWorkoutPlanNetworkService
    ): WorkoutPlanNetworkService
}