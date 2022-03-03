package com.litus_animae.refitted.data.room

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.litus_animae.refitted.data.WorkoutPlanNetworkService
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.models.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

class RoomCacheWorkoutPlanRepository @Inject constructor(
    private val database: RefittedRoom,
    networkService: WorkoutPlanNetworkService
): WorkoutPlanRepository {
    @OptIn(ExperimentalPagingApi::class)
    override val workouts: Flow<PagingData<WorkoutPlan>> =
        Pager(
            config = PagingConfig(pageSize = 10),
            remoteMediator = WorkoutPlanRemoteMediator(database, networkService)
        ) {
            database.getWorkoutPlanDao().pagingSource()
        }.flow

    override fun workoutByName(name: String): Flow<WorkoutPlan?> {
        return database.getWorkoutPlanDao().planByName(name)
    }
}