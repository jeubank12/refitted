package com.litus_animae.refitted.data.room

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.models.SavedState
import com.litus_animae.refitted.models.WorkoutPlan
import com.litus_animae.refitted.util.SavedStateKeys
import com.litus_animae.refitted.util.exception.UserNotLoggedInException
import java.time.Instant
import java.time.temporal.ChronoUnit


@OptIn(ExperimentalPagingApi::class)
class WorkoutPlanRemoteMediator(
  private val database: RefittedRoom,
  private val networkService: WorkoutPlanNetworkService
) : RemoteMediator<Int, WorkoutPlan>() {
  private val workoutPlanDao = database.getWorkoutPlanDao()
  private val savedStateDao = database.getSavedStateDao()

  override suspend fun initialize(): InitializeAction {
    Log.d(TAG, "initializing")
    val workoutsLastRefreshed = savedStateDao
      .loadState(SavedStateKeys.CacheTimeKey)?.value?.toLongOrNull()
      ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
    val nextRefreshDate = Instant.ofEpochMilli(workoutsLastRefreshed)
      .plus(CacheLimitHours, ChronoUnit.HOURS)
    val now = Instant.now()
    return if (nextRefreshDate.isBefore(now)) InitializeAction.LAUNCH_INITIAL_REFRESH
    else InitializeAction.SKIP_INITIAL_REFRESH
  }

  override suspend fun load(
    loadType: LoadType,
    state: PagingState<Int, WorkoutPlan>
  ): MediatorResult {
    when (loadType) {
      LoadType.PREPEND, LoadType.APPEND -> return MediatorResult.Success(endOfPaginationReached = true)
      else -> {}
    }
    try {
      val plans: List<WorkoutPlan> = networkService.getWorkoutPlans()
      database.withTransaction {
        val currentPlansByName = workoutPlanDao.getAll().associateBy { it.workout }
        workoutPlanDao.clearAll()
        val upsertPlans = plans.map { newPlan ->
          val existingPlan = currentPlansByName[newPlan.workout] ?: return@map newPlan
          existingPlan.copy(
            totalDays = newPlan.totalDays,
            restDays = newPlan.restDays,
            description = newPlan.description,
            globalAlternateLabels = newPlan.globalAlternateLabels,
            globalAlternate = existingPlan.globalAlternate ?: newPlan.globalAlternate
          )
        }
        workoutPlanDao.insertAll(upsertPlans)
        savedStateDao.insert(
          SavedState(
            SavedStateKeys.CacheTimeKey,
            Instant.now().toEpochMilli().toString()
          )
        )
      }
      return MediatorResult.Success(endOfPaginationReached = true)
    } catch (ex: UserNotLoggedInException){
      Log.w(TAG, ex.message ?: "user not logged in")
      return MediatorResult.Error(ex)
    } catch (ex: com.amazonaws.services.cognitoidentity.model.NotAuthorizedException) {
      Log.e(TAG, ex.message ?: "NotAuthorizedException")
      return MediatorResult.Error(ex)
    } catch (ex: com.amazonaws.services.cognitoidentity.model.InvalidIdentityPoolConfigurationException) {
      Log.e(TAG, ex.message ?: "NotAuthorizedException")
      return MediatorResult.Error(ex)
    }
  }

  companion object {
    private const val TAG = "WorkoutPlanRemoteMediator"
    private const val CacheLimitHours = 12L
  }
}