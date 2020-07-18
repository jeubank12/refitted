package com.litus_animae.refitted.models;

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

class ExerciseTest {
    private val testExercise = Exercise()

    @BeforeEach
    fun setUp() {
        testExercise.id = "Tricep_Alternating Woodchopper Pushdowns"
        testExercise.workout = "AX1"
        testExercise.description = "Alternate reps (10-12 in each direction) for each completed set. If using a resistance band, step further away to increase tension on band and difficulty of exercise"
    }

    @Test
    fun getId() {
        assertThat(testExercise.id).isEqualTo("Tricep_Alternating Woodchopper Pushdowns")
    }

    @Test
    fun setId() {
        testExercise.id = "test"
        assertThat(testExercise.id).isEqualTo("test")
    }

    @Test
    fun getName() {
        assertThat(testExercise.name).isEqualTo("Alternating Woodchopper Pushdowns")
    }

    @Test
    fun getNameFromNull() {
        testExercise.id = ""
        assertThat(testExercise.getName(true)).isNull()
    }

    @Test
    fun getNameDisallowNull() {
        testExercise.id = ""
        assertThat(testExercise.getName(false)).isNotNull()
        assertThat(testExercise.getName(false)).isEmpty()
    }

    @Test
    fun setName() {
        testExercise.setName("test")
        assertThat(testExercise.id).isEqualTo(testExercise.category + "_test")
    }

    @Test
    fun getCategory() {
        assertThat(testExercise.category).isEqualTo("Tricep")
    }

    @Test
    fun setCategory() {
        testExercise.category = "test"
        assertThat(testExercise.id).isEqualTo("test_" + testExercise.name)
    }
}