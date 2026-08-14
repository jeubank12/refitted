package com.litus_animae.refitted.data.room

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
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

    override val accessibleWorkouts: Flow<List<WorkoutPlan>> =
        workoutPlanDao.getServerPlans().map { plans -> plans.map { it.toDomain() } }

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

    override suspend fun addRestDayToCustomPlan(workoutPlan: WorkoutPlan): Int {
        val currentPlan = workoutPlanDao.getByName(workoutPlan.workout)
            ?: RoomWorkoutPlan.fromDomain(workoutPlan)
        val newDay = currentPlan.totalDays + 1
        workoutPlanDao.update(
            currentPlan.copy(totalDays = newDay, restDays = currentPlan.restDays + newDay)
        )
        return newDay
    }

    override suspend fun copyCustomDay(workoutPlan: WorkoutPlan, fromDay: Int): Int {
        val exerciseDao = database.getExerciseDao()
        val currentPlan = workoutPlanDao.getByName(workoutPlan.workout)
            ?: RoomWorkoutPlan.fromDomain(workoutPlan)
        val newDay = currentPlan.totalDays + 1

        val sourceSets = exerciseDao.loadDayExerciseSets(fromDay.toString(), workoutPlan.workout)
        val copiedSets = sourceSets.map { source -> source.copy(day = newDay.toString()) }
        exerciseDao.storeExerciseSets(copiedSets)

        workoutPlanDao.update(currentPlan.copy(totalDays = newDay))
        return newDay
    }

    override suspend fun clearCustomDay(workoutPlan: WorkoutPlan, day: Int) {
        database.getExerciseDao().clearDay(day.toString(), workoutPlan.workout)
    }

    override suspend fun setCustomDayRest(workoutPlan: WorkoutPlan, day: Int, isRest: Boolean) {
        val currentPlan = workoutPlanDao.getByName(workoutPlan.workout)
            ?: RoomWorkoutPlan.fromDomain(workoutPlan)
        val newRestDays = if (isRest) currentPlan.restDays + day else currentPlan.restDays - day
        workoutPlanDao.update(currentPlan.copy(restDays = newRestDays))
        if (isRest) {
            database.getExerciseDao().clearDay(day.toString(), workoutPlan.workout)
        }
    }

    override suspend fun renameCustomPlan(oldName: String, newName: String): Result<Unit> {
        val exerciseDao = database.getExerciseDao()
        return database.withTransaction {
            // Checked inside the transaction, not before it, so a concurrent write can't insert
            // or rename a plan to newName between the check and the rename below.
            if (workoutPlanDao.getByName(newName) != null) {
                return@withTransaction Result.failure(IllegalStateException("A plan named \"$newName\" already exists."))
            }
            // exerciseset has a FK to Exercise on (name, workout) with no onUpdate action, and
            // Room runs with PRAGMA foreign_keys=ON, so renaming either side alone trips an
            // immediate FK violation - defer all FK checks in this transaction until commit,
            // when both sides are consistent again.
            database.openHelper.writableDatabase.execSQL("PRAGMA defer_foreign_keys = ON")
            workoutPlanDao.renamePlan(oldName, newName)
            exerciseDao.renameExerciseWorkout(oldName, newName)
            exerciseDao.renameExerciseSetWorkout(oldName, newName)
            exerciseDao.renameSetRecordWorkout(oldName, newName)
            Result.success(Unit)
        }
    }

    override suspend fun deleteCustomPlan(name: String) {
        val exerciseDao = database.getExerciseDao()
        database.withTransaction {
            exerciseDao.deleteExerciseSetsForWorkout(name)
            exerciseDao.deleteSetRecordsForWorkout(name)
            exerciseDao.deleteExercisesForWorkout(name)
            workoutPlanDao.deletePlan(name)
        }
    }
}