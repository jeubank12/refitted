package com.litus_animae.refitted.data

import com.litus_animae.refitted.models.WorkoutPlan

interface WorkoutPlanNetworkService {
suspend fun getWorkoutPlans(): List<WorkoutPlan>
}