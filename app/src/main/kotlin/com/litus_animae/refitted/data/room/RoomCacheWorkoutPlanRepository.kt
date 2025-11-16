package com.litus_animae.refitted.data.room

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.litus_animae.refitted.data.WorkoutPlanRepository
import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.room.entities.RoomWorkoutPlan
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
        }.flow.map { pagingData ->
            pagingData.map { roomPlan: RoomWorkoutPlan -> roomPlan.toDomain() }
        }.flowOn(Dispatchers.IO)

    override fun workoutByName(name: String): Flow<WorkoutPlan?> {
        return workoutPlanDao.planByName(name).map { it?.toDomain() }
    }

    override suspend fun setWorkoutLastViewedDay(workoutPlan: WorkoutPlan, day: Int) {
        return workoutPlanDao.update(RoomWorkoutPlan.fromDomain(workoutPlan.copy(lastViewedDay = day)))
    }

    override suspend fun setWorkoutStartDate(workoutPlan: WorkoutPlan, startDate: Instant) {
        return workoutPlanDao.update(RoomWorkoutPlan.fromDomain(workoutPlan.copy(workoutStartDate = startDate)))
    }

    override suspend fun setWorkoutGlobalAlternate(workoutPlan: WorkoutPlan, index: Int) {
        return workoutPlanDao.update(RoomWorkoutPlan.fromDomain(workoutPlan.copy(globalAlternate = index)))
    }
}