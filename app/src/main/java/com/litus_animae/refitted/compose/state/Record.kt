package com.litus_animae.refitted.compose.state

import com.litus_animae.refitted.models.ExerciseSet

data class Record(
    val weight: Double,
    val reps: Int,
    val set: ExerciseSet,
    val cumulativeReps: Int = 0,
    val stored: Boolean = false
)
