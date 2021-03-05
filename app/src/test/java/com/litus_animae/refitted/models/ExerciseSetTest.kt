package com.litus_animae.refitted.models;

import androidx.lifecycle.MutableLiveData
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ExerciseSetTest {
    private var mutableExerciseSet = DynamoExerciseSet(
            id = "1.5",
            name = "Tricep_Alternating Woodchopper Pushdowns"
    )
    private var testExerciseSet = ExerciseSet(RoomExerciseSet(mutableExerciseSet), MutableLiveData())

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
        testExerciseSet = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                workout = "AX1",
                id = "1.5.a",
                name = "Tricep_Alternating Woodchopper Pushdowns"
        )), MutableLiveData())
        assertThat(testExerciseSet.step).isEqualTo("5.a")
    }

    @Test
    fun getExerciseName() {
        assertThat(testExerciseSet.exerciseName).isEqualTo("Alternating Woodchopper Pushdowns")
    }

    @Test
    fun getExerciseNameNotNullNotFound() {
        testExerciseSet = ExerciseSet(RoomExerciseSet(DynamoExerciseSet(
                id = "1.5.a",
                name = "test"
        )), MutableLiveData())
        assertThat(testExerciseSet.exerciseName).isEqualTo("")
    }
}