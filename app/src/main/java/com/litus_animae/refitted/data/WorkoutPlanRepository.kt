package com.litus_animae.refitted.data

import androidx.paging.PagingData
import com.litus_animae.refitted.models.WorkoutPlan
import kotlinx.coroutines.flow.Flow

interface WorkoutPlanRepository {
    val workouts: Flow<PagingData<WorkoutPlan>>
    fun workoutByName(name: String): Flow<WorkoutPlan?>
    suspend fun setWorkoutLastViewedDay(workoutPlan: WorkoutPlan, day: Int)
}