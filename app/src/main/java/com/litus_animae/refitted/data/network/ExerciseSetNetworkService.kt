package com.litus_animae.refitted.data.network

import com.litus_animae.refitted.models.DayAndWorkout
import com.litus_animae.refitted.models.ExerciseSet

interface ExerciseSetNetworkService {
    suspend fun getExerciseSets(dayAndWorkout: DayAndWorkout): List<ExerciseSet>
}