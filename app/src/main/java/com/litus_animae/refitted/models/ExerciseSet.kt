package com.litus_animae.refitted.models

import arrow.core.None
import arrow.core.Option
import arrow.core.toOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class ExerciseSet(private val roomExerciseSet: RoomExerciseSet,
                       val exercise: Flow<Exercise>
) {
    val workout: String = roomExerciseSet.workout
    val day: String = roomExerciseSet.day
    val step: String = roomExerciseSet.step
    val id: String = "$day.$step"
    val name: String = roomExerciseSet.name
    val note: String = roomExerciseSet.note
    val reps: Int = roomExerciseSet.reps
    val sets: Int = roomExerciseSet.sets
    val isToFailure: Boolean = roomExerciseSet.isToFailure
    val rest: Int = roomExerciseSet.rest
    val repsUnit: String = roomExerciseSet.repsUnit
    val repsRange: Int = roomExerciseSet.repsRange

    val isActive = MutableStateFlow(true)

    val exerciseName: String
        get() = if (name.isEmpty() || !name.contains("_")) ""
        else name.split("_".toRegex(), 2).toTypedArray().getOrNull(1) ?: ""

    fun getAlternate(sets: List<ExerciseSet>): Option<ExerciseSet> {
        return getAlternateIndex(sets)
                .flatMap { index -> sets.getOrNull(index).toOption() }
    }

    fun getAlternateIndex(sets: List<ExerciseSet>): Option<Int> {
        if (hasAlternate){
            val prefix = step.removeSuffix(".a").removeSuffix(".b")
            return sets.indexOfFirst {
                set -> set.step.startsWith(prefix) && set.step != step
            }.toOption()
        }
        return None
    }

    val primaryStep = step.removeSuffix(".a").removeSuffix(".b")

    val hasAlternate = step.endsWith(".a") || step.endsWith(".b")

}