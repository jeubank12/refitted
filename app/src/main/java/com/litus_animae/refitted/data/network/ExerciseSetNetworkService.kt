package com.litus_animae.refitted.data.network

import com.litus_animae.refitted.models.*

data class NetworkExerciseSet(
    val set: MutableExerciseSet,
    val exercise: Exercise
)

interface ExerciseSetNetworkService {
    suspend fun getExerciseSets(dayAndWorkout: DayAndWorkout): List<NetworkExerciseSet>
}