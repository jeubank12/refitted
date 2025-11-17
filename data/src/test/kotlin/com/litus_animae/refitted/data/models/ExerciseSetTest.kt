package com.litus_animae.refitted.data.models

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test

class ExerciseSetTest {
    private var testExerciseSet = ExerciseSet(
        workout = "TestWorkout",
        day = "1",
        step = "5",
        name = "Tricep_Alternating Woodchopper Pushdowns",
        note = "",
        reps = 10,
        sets = 3,
        isToFailure = false,
        rest = 60,
        repsUnit = "reps",
        repsRange = 0,
        timeLimit = null,
        timeLimitUnit = null,
        repsSequence = emptyList(),
        exercise = flowOf(null)
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
            day = "1",
            step = "5.a",
            name = "Tricep_Alternating Woodchopper Pushdowns",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
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
            workout = "",
            day = "1",
            step = "5.a",
            name = "test",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(testExerciseSet.exerciseName).isEqualTo("")
    }

    @Test
    fun timeLimitMilliseconds_returnsNull_whenUnitIsNull() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = 10,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.timeLimitMilliseconds).isNull()
    }

    @Test
    fun timeLimitMilliseconds_calculatesSeconds() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = 10,
            timeLimitUnit = "seconds",
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.timeLimitMilliseconds).isEqualTo(10000)
    }

    @Test
    fun timeLimitMilliseconds_calculatesMinutes() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = 2,
            timeLimitUnit = "minutes",
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.timeLimitMilliseconds).isEqualTo(120000)
    }

    @Test
    fun exerciseName_whenNameIsEmpty_isEmpty() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.exerciseName).isEqualTo("")
    }

    @Test
    fun exerciseName_whenNameStartsWithUnderscore_isCorrect() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "_dog",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.exerciseName).isEqualTo("dog")
    }

    @Test
    fun reps_whenNotSequenced() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "",
            note = "",
            reps = 12,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.repsAreSequenced).isFalse()
        assertThat(exSet.reps(0)).isEqualTo(12)
        assertThat(exSet.reps(10)).isEqualTo(12)
    }

    @Test
    fun reps_whenSequenced_andIndexIsInBounds() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = listOf(10, 12, 14),
            exercise = flowOf(null)
        )
        assertThat(exSet.repsAreSequenced).isTrue()
        assertThat(exSet.reps(1)).isEqualTo(12)
    }

    @Test
    fun reps_whenSequenced_andIndexIsTooLow_returnsFirst() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = listOf(10, 12, 14),
            exercise = flowOf(null)
        )
        assertThat(exSet.repsAreSequenced).isTrue()
        assertThat(exSet.reps(-1)).isEqualTo(10)
    }

    @Test
    fun reps_whenSequenced_andIndexIsTooHigh_returnsLast() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "1",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = listOf(10, 12, 14),
            exercise = flowOf(null)
        )
        assertThat(exSet.repsAreSequenced).isTrue()
        assertThat(exSet.reps(5)).isEqualTo(14)
    }

    @Test
    fun isSuperSet_isFalse_forSimpleStep() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "2",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.isSuperSet).isFalse()
        assertThat(exSet.superSetStep).isNull()
    }

    @Test
    fun isSuperSet_isFalse_forAlternateStep() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "2.a",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.isSuperSet).isFalse()
        assertThat(exSet.superSetStep).isNull()
    }

    @Test
    fun isSuperSet_isTrue_forSuperSetStep() {
        val exSet = ExerciseSet(
            workout = "",
            day = "1",
            step = "2.3",
            name = "",
            note = "",
            reps = 0,
            sets = 0,
            isToFailure = false,
            rest = 0,
            repsUnit = "",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList(),
            exercise = flowOf(null)
        )
        assertThat(exSet.isSuperSet).isTrue()
        assertThat(exSet.superSetStep).isEqualTo(2) // 3-1
    }
}
