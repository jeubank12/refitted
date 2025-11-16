package com.litus_animae.refitted.models

import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.models.dynamo.MutableExerciseSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun timeLimitMilliseconds_returnsNull_whenUnitIsNull() {
        val roomSet = RoomExerciseSet(
            MutableExerciseSet(
                id = "1.1",
                timeLimit = 10,
                timeLimitUnit = null
            )
        )
        val exSet = ExerciseSet(roomSet, MutableStateFlow(null))
        assertThat(exSet.timeLimitMilliseconds).isNull()
    }

    @Test
    fun timeLimitMilliseconds_calculatesSeconds() {
        val roomSet = RoomExerciseSet(
            MutableExerciseSet(
                id = "1.1",
                timeLimit = 10,
                timeLimitUnit = "seconds"
            )
        )
        val exSet = ExerciseSet(roomSet, MutableStateFlow(null))
        assertThat(exSet.timeLimitMilliseconds).isEqualTo(10000)
    }

    @Test
    fun timeLimitMilliseconds_calculatesMinutes() {
        val roomSet = RoomExerciseSet(
            MutableExerciseSet(
                id = "1.1",
                timeLimit = 2,
                timeLimitUnit = "minutes"
            )
        )
        val exSet = ExerciseSet(roomSet, MutableStateFlow(null))
        assertThat(exSet.timeLimitMilliseconds).isEqualTo(120000)
    }

    @Test
    fun exerciseName_whenNameIsEmpty_isEmpty() {
        val exSet = ExerciseSet(
            RoomExerciseSet(MutableExerciseSet(id = "1.1", name = "")),
            MutableStateFlow(null)
        )
        assertThat(exSet.exerciseName).isEqualTo("")
    }

    @Test
    fun exerciseName_whenNameStartsWithUnderscore_isCorrect() {
        val exSet = ExerciseSet(
            RoomExerciseSet(MutableExerciseSet(id = "1.1", name = "_dog")),
            MutableStateFlow(null)
        )
        assertThat(exSet.exerciseName).isEqualTo("dog")
    }

    @Test
    fun reps_whenNotSequenced() {
        val roomSet = RoomExerciseSet(
            MutableExerciseSet(
                id = "1.1",
                reps = 12,
                repsSequence = ""
            )
        )
        val exSet = ExerciseSet(roomSet, flowOf(null))
        assertThat(exSet.repsAreSequenced).isFalse()
        assertThat(exSet.reps(0)).isEqualTo(12)
        assertThat(exSet.reps(10)).isEqualTo(12)
    }

    @Test
    fun reps_whenSequenced_andIndexIsInBounds() {
        val roomSet = RoomExerciseSet(
            MutableExerciseSet(
                id = "1.1",
                repsSequence = "10,12,14"
            )
        )
        val exSet = ExerciseSet(roomSet, flowOf(null))
        assertThat(exSet.repsAreSequenced).isTrue()
        assertThat(exSet.reps(1)).isEqualTo(12)
    }

    @Test
    fun reps_whenSequenced_andIndexIsTooLow_returnsFirst() {
        val roomSet = RoomExerciseSet(
            MutableExerciseSet(
                id = "1.1",
                repsSequence = "10,12,14"
            )
        )
        val exSet = ExerciseSet(roomSet, flowOf(null))
        assertThat(exSet.repsAreSequenced).isTrue()
        assertThat(exSet.reps(-1)).isEqualTo(10)
    }

    @Test
    fun reps_whenSequenced_andIndexIsTooHigh_returnsLast() {
        val roomSet = RoomExerciseSet(
            MutableExerciseSet(
                id = "1.1",
                repsSequence = "10,12,14"
            )
        )
        val exSet = ExerciseSet(roomSet, flowOf(null))
        assertThat(exSet.repsAreSequenced).isTrue()
        assertThat(exSet.reps(5)).isEqualTo(14)
    }

    @Test
    fun isSuperSet_isFalse_forSimpleStep() {
        val exSet = ExerciseSet(
            RoomExerciseSet(MutableExerciseSet(id = "1.2")),
            MutableStateFlow(null)
        )
        assertThat(exSet.isSuperSet).isFalse()
        assertThat(exSet.superSetStep).isNull()
    }

    @Test
    fun isSuperSet_isFalse_forAlternateStep() {
        val exSet = ExerciseSet(
            RoomExerciseSet(MutableExerciseSet(id = "1.2.a")),
            MutableStateFlow(null)
        )
        assertThat(exSet.isSuperSet).isFalse()
        assertThat(exSet.superSetStep).isNull()
    }

    @Test
    fun isSuperSet_isTrue_forSuperSetStep() {
        val exSet = ExerciseSet(
            RoomExerciseSet(MutableExerciseSet(id = "1.2.3")),
            MutableStateFlow(null)
        )
        assertThat(exSet.isSuperSet).isTrue()
        assertThat(exSet.superSetStep).isEqualTo(2) // 3-1
    }
}
