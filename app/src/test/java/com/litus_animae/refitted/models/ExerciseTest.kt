package com.litus_animae.refitted.models;

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

class ExerciseTest {
    private var testExercise = Exercise(
            workout = "AX1",
            id = "Tricep_Alternating Woodchopper Pushdowns"
    )

    @Test
    fun getId() {
        assertThat(testExercise.id).isEqualTo("Tricep_Alternating Woodchopper Pushdowns")
    }

    @Test
    fun setId() {
        testExercise = Exercise(workout = "AX1", id = "test")
        assertThat(testExercise.id).isEqualTo("test")
    }

    @Test
    fun getName() {
        assertThat(testExercise.name).isEqualTo("Alternating Woodchopper Pushdowns")
    }

    @Test
    fun getNameFromNull() {
        testExercise = Exercise(workout = "AX1", id = "")
        assertThat(testExercise.getName(true)).isNull()
    }

    @Test
    fun getNameDisallowNull() {
        testExercise = Exercise(workout = "AX1", id = "")
        assertThat(testExercise.getName(false)).isNotNull()
        assertThat(testExercise.getName(false)).isEmpty()
    }
}