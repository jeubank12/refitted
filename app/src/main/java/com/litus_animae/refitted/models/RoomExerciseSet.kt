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
    val timeLimitUnit: String?
) {


    constructor(mutableExerciseSet: MutableExerciseSet) : this(
        mutableExerciseSet.workout,
        mutableExerciseSet.id.split("\\.".toRegex(), 2).toTypedArray().getOrElse(0) { "" },
        mutableExerciseSet.id.split("\\.".toRegex(), 2).toTypedArray().getOrElse(1) { "" },
        mutableExerciseSet.name,
        mutableExerciseSet.note,
        mutableExerciseSet.reps,
        mutableExerciseSet.sets,
        mutableExerciseSet.isToFailure,
        mutableExerciseSet.rest,
        mutableExerciseSet.repsUnit,
        mutableExerciseSet.repsRange,
        mutableExerciseSet.timeLimit,
        mutableExerciseSet.timeLimitUnit
    )

    constructor(exerciseSet: ExerciseSet) : this(
        exerciseSet.workout,
        exerciseSet.day,
        exerciseSet.step,
        exerciseSet.exerciseName,
        exerciseSet.note,
        exerciseSet.reps,
        exerciseSet.sets,
        exerciseSet.isToFailure,
        exerciseSet.rest,
        exerciseSet.repsUnit,
        exerciseSet.repsRange,
        null,
        null
    )
}