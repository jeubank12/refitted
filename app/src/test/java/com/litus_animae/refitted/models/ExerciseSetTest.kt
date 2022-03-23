package com.litus_animae.refitted.models;

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test

class ExerciseSetTest {
    private var mutableExerciseSet = MutableExerciseSet(
        id = "1.5",
        name = "Tricep_Alternating Woodchopper Pushdowns"
    )
    private var testExerciseSet =
        ExerciseSet(RoomExerciseSet(mutableExerciseSet), MutableStateFlow(null))

    @Test
    fun getDay() {
        assertThat(testExerciseSet.day).isEqualTo("1")
    }

    @Test
    fun getSet() {
        assertThat(testExerciseSet.step).isEqualTo("5")
    }

    @Test
    fun getStepWithAlternate() {
        testExerciseSet = ExerciseSet(
            RoomExerciseSet(
                MutableExerciseSet(
                    workout = "AX1",
                    id = "1.5.a",
                    name = "Tricep_Alternating Woodchopper Pushdowns"
                )
            ), MutableStateFlow(null)
        )
        assertThat(testExerciseSet.step).isEqualTo("5.a")
    }

    @Test
    fun getExerciseName() {
        assertThat(testExerciseSet.exerciseName).isEqualTo("Alternating Woodchopper Pushdowns")
    }

    @Test
    fun getExerciseNameNotNullNotFound() {
        testExerciseSet = ExerciseSet(
            RoomExerciseSet(
                MutableExerciseSet(
                    id = "1.5.a",
                    name = "test"
                )
            ), MutableStateFlow(null)
        )
        assertThat(testExerciseSet.exerciseName).isEqualTo("")
    }
}