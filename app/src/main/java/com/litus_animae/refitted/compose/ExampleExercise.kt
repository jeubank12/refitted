package com.litus_animae.refitted.compose

import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.MutableExercise
import com.litus_animae.refitted.models.RoomExerciseSet
import kotlinx.coroutines.flow.flow

fun getExampleExerciseSet(): ExerciseSet {
    val exercise = Exercise(
        MutableExercise(
            workout = "AX1",
            id = "1.1",
            description = "Lift the weight over your head then set it back down. Repeat for each repetition of the exercise until all sets are complete"
        )
    )
    val exerciseSet = ExerciseSet(RoomExerciseSet(
        workout = "AX1",
        day = "1",
        step = "1",
        name = "Do Things",
        note = "Pick things up then put them back down. Enjoy yourself as you do it",
        reps = -1,
        sets = 3,
        isToFailure = true,
        rest = 60,
        repsUnit = "",
        repsRange = 0
    ), flow { emit(exercise) })
    return exerciseSet
}