package com.litus_animae.refitted.models

import androidx.annotation.NonNull
import androidx.lifecycle.LiveData
import androidx.room.*

@Entity(tableName = "exerciseset",
        primaryKeys = ["day", "step", "workout"],
        foreignKeys = [ForeignKey(entity = Exercise::class,
                parentColumns = ["exercise_name", "exercise_workout"],
                childColumns = ["name", "workout"])],
        indices = [Index(value = ["name", "workout"])])
data class RoomExerciseSet(
    val workout: String,
    val id: String,
    val name: String,
    val note: String,
    val reps: Int,
    val sets: Int,
    @ColumnInfo(name = "toFailure")
    val isToFailure: Boolean,
    val rest: Int,
    val repsUnit: String,
    val repsRange: Int) {

constructor(workout: String, day: String, step: String, name: String, note: String, reps: Int,
sets:Int, isToFailure: Boolean, rest: Int, repsUnit: String, repsRange: Int) : this(
        workout,
        "$day.$step",
name, note, reps, sets, isToFailure, rest, repsUnit, repsRange)

    constructor(mutableExerciseSet: MutableExerciseSet): this(
            mutableExerciseSet.workout,
            mutableExerciseSet.id,
            mutableExerciseSet.name,
            mutableExerciseSet.note,
            mutableExerciseSet.reps,
            mutableExerciseSet.sets,
            mutableExerciseSet.isToFailure,
            mutableExerciseSet.rest,
            mutableExerciseSet.repsUnit,
            mutableExerciseSet.repsRange)

    val day: String = id.split("\\.".toRegex(), 2).toTypedArray().getOrNull(0) ?: ""

    val step: String = id.split("\\.".toRegex(), 2).toTypedArray().getOrNull(1) ?: ""
}