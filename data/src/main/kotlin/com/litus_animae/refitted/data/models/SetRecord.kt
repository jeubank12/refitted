package com.litus_animae.refitted.data.models

import java.time.Instant

/**
 * Domain model representing a completed set record.
 * Pure domain object with no persistence concerns.
 */
data class SetRecord(
  val weight: Double,
  val reps: Int,
  val workout: String,
  val targetSet: String,
  val completed: Instant,
  val exercise: String
) {
  /**
   * Convenience constructor from an ExerciseSet
   */
  constructor(weight: Double, reps: Int, targetExerciseSet: ExerciseSet) : this(
    weight,
    reps,
    targetExerciseSet.workout,
    targetExerciseSet.id,
    Instant.now(),
    targetExerciseSet.exerciseName
  )

  override fun equals(other: Any?): Boolean {
    val record = other as? SetRecord ?: return false
    return record.workout == workout &&
      record.targetSet == targetSet &&
      record.reps == reps &&
      record.weight == weight &&
      record.completed == completed
  }

  override fun hashCode(): Int {
    var result = weight.hashCode()
    result = 31 * result + reps
    result = 31 * result + workout.hashCode()
    result = 31 * result + targetSet.hashCode()
    result = 31 * result + completed.hashCode()
    return result
  }
}
