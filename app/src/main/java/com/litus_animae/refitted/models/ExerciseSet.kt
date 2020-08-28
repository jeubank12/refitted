package com.litus_animae.refitted.models

import androidx.lifecycle.LiveData

data class ExerciseSet(private val roomExerciseSet: RoomExerciseSet,
                       val alternate: ExerciseSet?,
                       val exercise: LiveData<Exercise>) {
    val workout: String = roomExerciseSet.workout
    val id: String = roomExerciseSet.id
    val name: String = roomExerciseSet.name
    val note: String = roomExerciseSet.note
    val reps: Int = roomExerciseSet.reps
    val sets: Int = roomExerciseSet.sets
    val isToFailure: Boolean = roomExerciseSet.isToFailure
    val rest: Int = roomExerciseSet.rest
    val repsUnit: String = roomExerciseSet.repsUnit
    val repsRange: Int = roomExerciseSet.repsRange

    val day: String = id.split("\\.".toRegex(), 2).toTypedArray().getOrNull(0) ?: ""

    val step: String = id.split("\\.".toRegex(), 2).toTypedArray().getOrNull(1) ?: ""

    var isActive = true

    val exerciseName: String
        get() = if (name.isEmpty() || !name.contains("_")) ""
        else name.split("_".toRegex(), 2).toTypedArray().getOrNull(1) ?: ""

    fun hasAlternate(): Boolean {
        return alternate != null
    }

}