package com.litus_animae.refitted.models

import androidx.annotation.NonNull
import androidx.lifecycle.LiveData
import androidx.room.*
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*
import com.litus_animae.refitted.models.Exercise

@Entity(primaryKeys = ["day", "step", "workout"],
        foreignKeys = [ForeignKey(entity = Exercise::class,
                parentColumns = ["exercise_name", "exercise_workout"],
                childColumns = ["name", "workout"])],
        indices = [Index(value = ["name", "workout"])])
data class ExerciseSet(
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

    @NonNull
    var day: String = id.split("\\.".toRegex(), 2).toTypedArray().getOrNull(0) ?: ""

    @NonNull
    var step: String = id.split("\\.".toRegex(), 2).toTypedArray().getOrNull(1) ?: ""

    @Ignore
    var exercise: LiveData<Exercise>? = null

    @Ignore
    var alternate: ExerciseSet? = null

    @Ignore
    var isActive = true

    val exerciseName: String
        get() = if (name.isEmpty() || !name.contains("_")) ""
        else name.split("_".toRegex(), 2).toTypedArray().getOrNull(1) ?: ""

    fun hasAlternate(): Boolean {
        return alternate != null
    }

}