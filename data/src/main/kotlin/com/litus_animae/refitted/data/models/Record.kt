package com.litus_animae.refitted.data.models

import java.time.Instant

/**
 * Domain model representing a single exercise record with accumulated stats.
 */
data class Record(
    val weight: Double,
    val reps: Int,
    val set: ExerciseSet,
    val completed: Instant,
    val cumulativeReps: Int = 0,
    val stored: Boolean = false
)
