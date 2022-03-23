package com.litus_animae.refitted.data.room

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.models.WorkoutPlan


@OptIn(ExperimentalPagingApi::class)
class WorkoutPlanRemoteMediator(
    private val database: RefittedRoom,
    private val networkService: WorkoutPlanNetworkService
): RemoteMediator<Int, WorkoutPlan>() {
    private val workoutPlanDao = database.getWorkoutPlanDao()

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
            val currentPlansByName = workoutPlanDao.getAll().associateBy { it.workout }
            workoutPlanDao.clearAll()
            val upsertPlans = plans.map{
                val existingPlan = currentPlansByName[it.workout] ?: return@map it
                existingPlan.copy(totalDays = it.totalDays)
            }
            workoutPlanDao.insertAll(upsertPlans)
        }
        return MediatorResult.Success(endOfPaginationReached = true)
    }
}