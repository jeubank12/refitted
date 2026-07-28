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

  /**
   * Admin-authored plans the user has access to, programs and equipment libraries alike - used by
   * the add-exercise picker to list which workouts' exercises can be browsed by muscle group, and
   * to order/section that list by [WorkoutPlan.kind].
   */
  val accessibleWorkouts: Flow<List<WorkoutPlan>>
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
   * Appends a new rest day to a custom plan and returns its day number.
   */
  suspend fun addRestDayToCustomPlan(workoutPlan: WorkoutPlan): Int

  /**
   * Copies [fromDay]'s exercises - with targets derived from completed sets - into a newly
   * appended day. Returns the day number written to.
   */
  suspend fun copyCustomDay(workoutPlan: WorkoutPlan, fromDay: Int): Int

  /**
   * Deletes all exercises on [day] of a custom plan, leaving the day slot (and its number) in
   * place, empty.
   */
  suspend fun clearCustomDay(workoutPlan: WorkoutPlan, day: Int)

  /**
   * Marks or unmarks [day] of a custom plan as a rest day. Marking a day as rest also clears its
   * exercises.
   */
  suspend fun setCustomDayRest(workoutPlan: WorkoutPlan, day: Int, isRest: Boolean)
}
