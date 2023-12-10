package com.litus_animae.refitted.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.litus_animae.refitted.models.dynamo.MutableExerciseSet

@Entity(
  tableName = "exerciseset",
  primaryKeys = ["day", "step", "workout"],
  foreignKeys = [ForeignKey(
    entity = Exercise::class,
    parentColumns = ["exercise_name", "exercise_workout"],
    childColumns = ["name", "workout"]
  )],
  indices = [Index(value = ["name", "workout"])]
)
data class RoomExerciseSet(
  val workout: String,
  val day: String,
  val step: String,
  val primaryStep: Int,
  val superSetStep: Int?,
  val alternateStep: String?,
  val name: String,
  val note: String,
  val reps: Int,
  val sets: Int,
  @ColumnInfo(name = "toFailure")
  val isToFailure: Boolean,
  val rest: Int,
  val repsUnit: String,
  val repsRange: Int,
  val timeLimit: Int?,
  val timeLimitUnit: String?,
  val repsSequence: List<Int>
) {


  constructor(mutableExerciseSet: MutableExerciseSet) : this(
    mutableExerciseSet.workout,
    day = mutableExerciseSet.id.split("\\.".toRegex(), 2).toTypedArray().getOrElse(0) { "" },
    step = mutableExerciseSet.id.split("\\.".toRegex(), 2).toTypedArray().getOrElse(1) { "" },
    primaryStep = parsePrimaryStep(mutableExerciseSet.id),
    superSetStep = parseSuperSetStep(mutableExerciseSet.id),
    alternateStep = parseAlternateStep(mutableExerciseSet.id),
    mutableExerciseSet.name,
    mutableExerciseSet.note,
    mutableExerciseSet.reps,
    mutableExerciseSet.sets,
    mutableExerciseSet.isToFailure,
    mutableExerciseSet.rest,
    mutableExerciseSet.repsUnit,
    mutableExerciseSet.repsRange,
    mutableExerciseSet.timeLimit,
    mutableExerciseSet.timeLimitUnit,
    mutableExerciseSet.repsSequence.split(',')
      .mapNotNull{ it.toIntOrNull() }
  )

  companion object {
    fun parseAlternateStep(input: String): String? =
      """^(\d+\.)+([a-z]+)""".toRegex().find(input)?.groupValues?.lastOrNull()

    fun parseSuperSetStep(input: String): Int? =
      """^(\d+\.){2,}(\d+)""".toRegex().find(input)?.groupValues?.lastOrNull()?.toIntOrNull()

    fun parsePrimaryStep(input: String): Int =
      """^\d+\.(\d+)""".toRegex().find(input)?.groupValues?.lastOrNull()?.toIntOrNull() ?: 0
  }
}