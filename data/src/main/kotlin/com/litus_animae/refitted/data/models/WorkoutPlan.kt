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
  val globalAlternate: Int? = null,
  val isCustom: Boolean = false,
  val kind: PlanKind = PlanKind.PROGRAM,
  // Stable identity independent of `workout` - a rename changes the display name but must not
  // change which row a UI list item's Compose key resolves to. Defaults to `workout` so every
  // existing call site (network deserialization, previews, tests) is unaffected; only a
  // persisted, already-created row can carry an `id` that differs from its current name.
  val id: String = workout
)

/**
 * Simple data class pairing a day with a workout ID.
 */
data class DayAndWorkout(val day: String, val workoutId: String)
