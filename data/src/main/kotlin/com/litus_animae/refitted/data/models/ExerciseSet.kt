package com.litus_animae.refitted.data.models

import kotlinx.coroutines.flow.Flow

/**
 * Domain model representing an exercise set within a workout.
 * Contains all exercise set data and business logic for parsing steps, supersets, etc.
 */
data class ExerciseSet(
  val workout: String,
  val day: String,
  val step: String,
  val name: String,
  val note: String,
  val reps: Int,
  val sets: Int,
  val isToFailure: Boolean,
  val rest: Int,
  val repsUnit: String,
  val repsRange: Int,
  val timeLimit: Int?,
  val timeLimitUnit: String?,
  val repsSequence: List<Int>,
  val exercise: Flow<Exercise?>
) {
  val id: String = "$day.$step"

  /**
   * Time limit converted to milliseconds
   */
  val timeLimitMilliseconds: Int? =
    if (timeLimitUnit != null) {
      timeLimit?.let {
        when (timeLimitUnit) {
          "minutes" -> it * 60000
          else -> it * 1000
        }
      }
    } else null

  /**
   * Extract exercise name from the name field (format: "{prefix}_{name}")
   */
  val exerciseName: String
    get() = if (name.isEmpty() || !name.contains("_")) {
      ""
    } else {
      name.split("_", limit = 2).getOrNull(1) ?: ""
    }

  /**
   * Get reps for a specific set index, accounting for sequenced reps
   */
  fun reps(currentSet: Int): Int {
    return if (!repsAreSequenced) {
      reps
    } else {
      repsSequence[currentSet.coerceIn(0, repsSequence.size - 1)]
    }
  }

  /**
   * Whether reps vary by set (sequenced)
   */
  val repsAreSequenced: Boolean = repsSequence.isNotEmpty()

  // Superset parsing logic
  private val superSetRegex = "^\\d+\\.(\\d+)".toRegex()
  private val superStepRegex = "(^\\d+)\\.".toRegex()

  /**
   * Primary step without alternate suffix (e.g., "1.2.a" -> "1.2")
   */
  val primaryStep: String = step.replace("\\.[a-z]$".toRegex(), "")

  /**
   * Super step number (first part of step, e.g., "1.2.3" -> "1")
   */
  val superStep: String = superStepRegex.find(step, 0)?.groupValues?.get(1).orEmpty()

  /**
   * 0-indexed number indicating where this superset is in the sequence
   * e.g., "1.2.1" -> 1, "1.2.2" -> 2
   */
  val superSetStep: Int? = superSetRegex.find(step, 0)?.groupValues?.get(1)
    ?.let { it.toInt() - 1 }

  /**
   * Whether this exercise is part of a superset
   */
  val isSuperSet: Boolean = superSetStep != null

  override fun toString(): String {
    return "ExerciseSet:$id"
  }
}
