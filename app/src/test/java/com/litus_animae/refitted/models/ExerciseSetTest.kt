package com.litus_animae.refitted.models;

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ExerciseSetTest {
    private var testExerciseSet = ExerciseSet(
            id = "1.5",
            name = "Tricep_Alternating Woodchopper Pushdowns"
    )

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
                workout = "AX1",
                id = "1.5.a",
                name = "Tricep_Alternating Woodchopper Pushdowns"
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
                id = "1.5.a",
                name = "test"
        )
        assertThat(testExerciseSet.exerciseName).isEqualTo("")
    }
}