package com.litus_animae.refitted.data.models

import java.time.Instant

/**
 * Domain model representing a workout plan/program.
 * Pure domain object with no persistence concerns.
 */
data class WorkoutPlan(
  val workout: String,
  val totalDays: Int = 84,
  val lastViewedDay: Int = 1,
  val workoutStartDate: Instant = Instant.ofEpochMilli(0),
  val restDays: List<Int> = emptyList(),
  val description: String = "",
  val globalAlternateLabels: List<String> = emptyList(),
  val globalAlternate: Int? = null
)

/**
 * Simple data class pairing a day with a workout ID.
 */
data class DayAndWorkout(val day: String, val workoutId: String)
