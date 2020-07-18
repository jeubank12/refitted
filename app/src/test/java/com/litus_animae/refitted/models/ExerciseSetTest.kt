package com.litus_animae.refitted.models;

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

class ExerciseSetTest {
    private val testExerciseSet = ExerciseSet()

    @BeforeEach
    fun setUp() {
        testExerciseSet.id = "1.5"
        testExerciseSet.name = "Tricep_Alternating Woodchopper Pushdowns"
    }

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
        testExerciseSet.id = "1.5.a"
        assertThat(testExerciseSet.step).isEqualTo("5.a")
    }

    @Test
    fun getExerciseName() {
        assertThat(testExerciseSet.exerciseName).isEqualTo("Alternating Woodchopper Pushdowns")
    }

    @Test
    fun getExerciseNameNotNullNotFound() {
        testExerciseSet.name = "test"
        assertThat(testExerciseSet.exerciseName).isNotNull()
        assertThat(testExerciseSet.exerciseName).isEqualTo("")
    }

    @Test
    fun getExerciseNameNotNull() {
        testExerciseSet.name = null
        assertThat(testExerciseSet.exerciseName).isNotNull()
        assertThat(testExerciseSet.exerciseName).isEqualTo("")
    }

    @Test
    fun getRepsUnitNotNull() {
        assertThat(testExerciseSet.repsUnit).isNotNull()
        assertThat(testExerciseSet.repsUnit).isEqualTo("")
    }
}