package com.litus_animae.refitted.data

import androidx.paging.PagingData
import com.litus_animae.refitted.data.models.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository interface for workout plan operations.
 * Abstracts data access for workout plans and user preferences.
 */
interface WorkoutPlanRepository {
  val workouts: Flow<PagingData<WorkoutPlan>>
  fun workoutByName(name: String): Flow<WorkoutPlan?>
  suspend fun setWorkoutLastViewedDay(workoutPlan: WorkoutPlan, day: Int)
  suspend fun setWorkoutStartDate(workoutPlan: WorkoutPlan, startDate: Instant)
  suspend fun setWorkoutGlobalAlternate(workoutPlan: WorkoutPlan, index: Int)

  /**
   * Creates a new empty, user-authored workout plan, already aligned to today.
   */
  suspend fun createCustomPlan(name: String): WorkoutPlan

  /**
   * Appends a new empty day to a custom plan and returns its day number.
   */
  suspend fun addDayToCustomPlan(workoutPlan: WorkoutPlan): Int

  /**
   * Copies [fromDay]'s exercises - with targets derived from completed sets - into [toDay].
   * A null [toDay] appends a new day. Returns the day number written to.
   */
  suspend fun copyCustomDay(workoutPlan: WorkoutPlan, fromDay: Int, toDay: Int?): Int
}
