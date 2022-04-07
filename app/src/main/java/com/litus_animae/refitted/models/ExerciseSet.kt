package com.litus_animae.refitted.models

import arrow.core.toOption
import kotlinx.coroutines.flow.Flow

data class ExerciseSet(
  private val roomExerciseSet: RoomExerciseSet,
  val exercise: Flow<Exercise?>
) {
  val workout: String = roomExerciseSet.workout
  val day: String = roomExerciseSet.day
  val step: String = roomExerciseSet.step
  val id: String = "$day.$step"
  private val name: String = roomExerciseSet.name
  val note: String = roomExerciseSet.note
  val reps: Int = roomExerciseSet.reps
  val sets: Int = roomExerciseSet.sets
  val isToFailure: Boolean = roomExerciseSet.isToFailure
  val rest: Int = roomExerciseSet.rest
  val repsUnit: String = roomExerciseSet.repsUnit
  val repsRange: Int = roomExerciseSet.repsRange
  val timeLimitMilliseconds: Long? =
    if (roomExerciseSet.timeLimitUnit != null) roomExerciseSet.timeLimit?.let {
      when (roomExerciseSet.timeLimitUnit) {
        "minutes" -> it * 60000L
        else -> it * 1000L
      }
    }
    else null

  val exerciseName: String
    get() = if (name.isEmpty() || !name.contains("_")) ""
    else name.split("_".toRegex(), 2).toTypedArray().getOrNull(1) ?: ""

  private val superSetRegex = "^\\d+\\.(\\d+)".toRegex()
  private val superStepRegex = "(^\\d+)\\.".toRegex()
  val primaryStep = step.replace("\\.[a-z]$".toRegex(), "")
  val superStep = superStepRegex.find(step, 0)?.groupValues?.get(1).orEmpty()

  /**
   * 0-indexed number indicating where this superset is in the sequence
   */
  val superSetStep = superSetRegex.find(step, 0)?.groupValues?.get(1).toOption()
    .map { it.toInt() - 1 }
  val isSuperSet = superSetStep.isNotEmpty()
}