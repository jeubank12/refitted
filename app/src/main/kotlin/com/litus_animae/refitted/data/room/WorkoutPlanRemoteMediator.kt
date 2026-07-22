package com.litus_animae.refitted.data.room

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.room.RefittedRoomProvider
import com.litus_animae.refitted.room.entities.RoomSavedState
import com.litus_animae.refitted.room.entities.RoomWorkoutPlan
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.SavedStateKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit


@OptIn(ExperimentalPagingApi::class)
class WorkoutPlanRemoteMediator(
  private val roomProvider: RefittedRoomProvider,
  private val networkService: WorkoutPlanNetworkService,
  private val log: LogUtil
) : RemoteMediator<Int, RoomWorkoutPlan>() {
  private val database by lazy { roomProvider.refittedRoom }
  private val workoutPlanDao by lazy { database.getWorkoutPlanDao() }
  private val savedStateDao by lazy { database.getSavedStateDao() }

  override suspend fun initialize(): InitializeAction {
    return withContext(Dispatchers.IO) {
    log.d(TAG, "initializing WorkoutPlanRemoteMediator on ${Thread.currentThread().name}")
      val workoutsLastRefreshed = savedStateDao
        .loadState(SavedStateKeys.CacheTimeKey)?.value?.toLongOrNull()
        ?: return@withContext InitializeAction.LAUNCH_INITIAL_REFRESH
      val nextRefreshDate = Instant.ofEpochMilli(workoutsLastRefreshed)
        .plus(CacheLimitHours, ChronoUnit.HOURS)
      val now = Instant.now()
      if (nextRefreshDate.isBefore(now)) InitializeAction.LAUNCH_INITIAL_REFRESH
      else InitializeAction.SKIP_INITIAL_REFRESH
    }
  }

  override suspend fun load(
    loadType: LoadType,
    state: PagingState<Int, RoomWorkoutPlan>
  ): MediatorResult {
    log.d(TAG, "Got request to load")
    when (loadType) {
      LoadType.PREPEND, LoadType.APPEND -> return MediatorResult.Success(endOfPaginationReached = true)
      else -> {}
    }
    log.d(TAG, "Reading plans")
    val plans: List<WorkoutPlan> = networkService.getWorkoutPlans()
    database.withTransaction {
      val currentPlansByName = workoutPlanDao.getAll().map { it.toDomain() }.associateBy { it.workout }
      workoutPlanDao.clearServerPlans()
      val upsertPlans = plans.map { newPlan ->
        val existingPlan = currentPlansByName[newPlan.workout] ?: return@map RoomWorkoutPlan.fromDomain(newPlan)
        RoomWorkoutPlan.fromDomain(existingPlan.copy(
          totalDays = newPlan.totalDays,
          restDays = newPlan.restDays,
          description = newPlan.description,
          globalAlternateLabels = newPlan.globalAlternateLabels,
          globalAlternate = existingPlan.globalAlternate ?: newPlan.globalAlternate
        ))
      }
      workoutPlanDao.insertAll(upsertPlans)
      savedStateDao.insert(
        RoomSavedState(
          SavedStateKeys.CacheTimeKey,
          Instant.now().toEpochMilli().toString()
        )
      )
    }
    return MediatorResult.Success(endOfPaginationReached = true)
  }

  companion object {
    private const val TAG = "WorkoutPlanRemoteMediator"
    private const val CacheLimitHours = 12L
  }
}