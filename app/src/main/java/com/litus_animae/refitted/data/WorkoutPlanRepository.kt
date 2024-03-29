package com.litus_animae.refitted.data

import androidx.paging.PagingData
import com.litus_animae.refitted.models.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WorkoutPlanRepository {
  val workouts: Flow<PagingData<WorkoutPlan>>
  fun workoutByName(name: String): Flow<WorkoutPlan?>
  suspend fun setWorkoutLastViewedDay(workoutPlan: WorkoutPlan, day: Int)
  suspend fun setWorkoutStartDate(workoutPlan: WorkoutPlan, startDate: Instant)
  suspend fun setWorkoutGlobalAlternate(workoutPlan: WorkoutPlan, index: Int)
}