package com.litus_animae.refitted.data.room

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.models.WorkoutPlan
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class RoomCacheWorkoutPlanRepository @Inject constructor(
    roomProvider: RefittedRoomProvider,
    networkService: WorkoutPlanNetworkService,
    log: LogUtil
) : WorkoutPlanRepository {

    private val database by lazy {roomProvider.refittedRoom}
    private val workoutPlanDao by lazy{ database.getWorkoutPlanDao()}
    @OptIn(ExperimentalPagingApi::class)
    override val workouts: Flow<PagingData<WorkoutPlan>> =
        Pager(
            config = PagingConfig(pageSize = 10),
            remoteMediator = WorkoutPlanRemoteMediator(roomProvider, networkService, log)
        ) {
            workoutPlanDao.pagingSource()
        }.flow

    override fun workoutByName(name: String): Flow<WorkoutPlan?> {
        return workoutPlanDao.planByName(name)
    }

    override suspend fun setWorkoutLastViewedDay(workoutPlan: WorkoutPlan, day: Int) {
        return workoutPlanDao.update(workoutPlan.copy(lastViewedDay = day))
    }

    override suspend fun setWorkoutStartDate(workoutPlan: WorkoutPlan, startDate: Instant) {
        return workoutPlanDao.update(workoutPlan.copy(workoutStartDate = startDate))
    }

    override suspend fun setWorkoutGlobalAlternate(workoutPlan: WorkoutPlan, index: Int) {
        return workoutPlanDao.update(workoutPlan.copy(globalAlternate = index))
    }
}