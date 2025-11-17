package com.litus_animae.refitted.data.network

import com.litus_animae.refitted.data.models.WorkoutPlan

/**
 * Network service interface for fetching workout plans.
 */
interface WorkoutPlanNetworkService {
  suspend fun getWorkoutPlans(): List<WorkoutPlan>
}
