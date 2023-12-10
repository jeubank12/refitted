package com.litus_animae.refitted.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.Instant

@Entity(primaryKeys = ["exercise", "completed"])
data class SetRecord(
  val weight: Double,
  val reps: Int,
  val workout: String,
  @ColumnInfo(name = "target_set") val targetSet: String,
  val completed: Instant,
  val exercise: String
) {
  constructor(weight: Double, reps: Int, targetExerciseSet: ExerciseSet) :
    this(
      weight,
      reps,
      targetExerciseSet.workout,
      targetExerciseSet.id,
      Instant.now(),
      targetExerciseSet.exerciseName
    )

  override fun equals(other: Any?): Boolean {
    val record = other as SetRecord?
    return record != null &&
      record.workout == workout &&
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