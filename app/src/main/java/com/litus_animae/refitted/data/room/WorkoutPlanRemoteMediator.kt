package com.litus_animae.refitted.data.room

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.litus_animae.refitted.data.WorkoutPlanNetworkService
import com.litus_animae.refitted.data.room.RefittedRoom
import com.litus_animae.refitted.models.WorkoutPlan


@OptIn(ExperimentalPagingApi::class)
class WorkoutPlanRemoteMediator(
    private val database: RefittedRoom,
    private val networkService: WorkoutPlanNetworkService
): RemoteMediator<Int, WorkoutPlan>() {
    // TODO initialize without refresh/as append
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, WorkoutPlan>
    ): MediatorResult {
        when(loadType){
            LoadType.PREPEND, LoadType.APPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            else -> {}
        }
        val plans: List<WorkoutPlan> = networkService.getWorkoutPlans()
        database.withTransaction {
            database.getWorkoutPlanDao().clearAll()
            database.getWorkoutPlanDao().insertAll(plans)
        }
        return MediatorResult.Success(endOfPaginationReached = true)
    }
}