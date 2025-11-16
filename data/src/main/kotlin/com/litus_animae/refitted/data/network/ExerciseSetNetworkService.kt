package com.litus_animae.refitted.data.network

import com.litus_animae.refitted.data.models.DayAndWorkout
import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.models.ExerciseSet

/**
 * Network data model pairing an exercise set with its exercise description.
 * Used for network responses.
 */
data class NetworkExerciseSet(
    val set: ExerciseSet,
    val exercise: Exercise
)

/**
 * Network service interface for fetching exercise sets.
 */
interface ExerciseSetNetworkService {
    suspend fun getExerciseSets(dayAndWorkout: DayAndWorkout): List<NetworkExerciseSet>
}
