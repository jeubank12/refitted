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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RoomCacheWorkoutPlanRepository @Inject constructor(
    private val roomProvider: RefittedRoomProvider,
    private val networkService: WorkoutPlanNetworkService,
    private val log: LogUtil
) : WorkoutPlanRepository {

    private val database by lazy {roomProvider.refittedRoom}
    private val workoutPlanDao by lazy{ database.getWorkoutPlanDao()}

    @OptIn(ExperimentalPagingApi::class)
    override val workouts: Flow<PagingData<WorkoutPlan>> =
        Pager<Int, RoomWorkoutPlan>(
            config = PagingConfig(pageSize = 10),
            remoteMediator = WorkoutPlanRemoteMediator(roomProvider, networkService, log)
        ) {
            workoutPlanDao.pagingSource()
        }.flow.map { pagingData ->
            pagingData.map { roomPlan -> roomPlan.toDomain() }
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

    override suspend fun createCustomPlan(name: String): WorkoutPlan {
        // Self-authored plans start "today" - there's no admin-defined day one to align to.
        val startOfToday = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val plan = RoomWorkoutPlan(
            workout = name,
            totalDays = 0,
            workoutStartDate = startOfToday,
            isCustom = true
        )
        workoutPlanDao.insertAll(listOf(plan))
        return plan.toDomain()
    }

    override suspend fun addDayToCustomPlan(workoutPlan: WorkoutPlan): Int {
        val currentPlan = workoutPlanDao.getByName(workoutPlan.workout)
            ?: RoomWorkoutPlan.fromDomain(workoutPlan)
        val newDay = currentPlan.totalDays + 1
        workoutPlanDao.update(currentPlan.copy(totalDays = newDay))
        return newDay
    }

    override suspend fun copyCustomDay(workoutPlan: WorkoutPlan, fromDay: Int, toDay: Int?): Int {
        val exerciseDao = database.getExerciseDao()
        val currentPlan = workoutPlanDao.getByName(workoutPlan.workout)
            ?: RoomWorkoutPlan.fromDomain(workoutPlan)
        val newDay = toDay ?: (currentPlan.totalDays + 1)

        val sourceSets = exerciseDao.loadDayExerciseSets(fromDay.toString(), workoutPlan.workout)
        // Targets come from what was actually completed, not the source day's (possibly still
        // open, sets = -1) definition - that's the point of "copy day".
        val completedByTargetSet = exerciseDao
            .loadDaySetRecords(workoutPlan.workout, fromDay.toString())
            .groupBy { it.targetSet }

        if (toDay != null) {
            exerciseDao.clearDay(newDay.toString(), workoutPlan.workout)
        }
        val copiedSets = sourceSets.map { source ->
            val completed = completedByTargetSet["$fromDay.${source.step}"]
            if (completed.isNullOrEmpty()) {
                source.copy(day = newDay.toString())
            } else {
                source.copy(day = newDay.toString(), sets = completed.size, reps = completed.last().reps)
            }
        }
        exerciseDao.storeExerciseSets(copiedSets)

        if (newDay > currentPlan.totalDays) {
            workoutPlanDao.update(currentPlan.copy(totalDays = newDay))
        }
        return newDay
    }
}