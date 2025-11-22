package com.litus_animae.refitted.data

import com.litus_animae.refitted.room.entities.RoomExercise
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import com.litus_animae.refitted.room.entities.RoomWorkoutPlan
import com.litus_animae.refitted.data.models.SetRecord
import java.time.Instant

/**
 * Test data builder for creating sample exercises, workout plans, and records
 * for integration tests.
 */
// TODO move to room and use in RoomFromDomainConversionTest?
object TestDataBuilder {

    const val TEST_WORKOUT = "TestWorkout"
    const val TEST_DAY = "1"

    /**
     * Creates a list of test exercise sets for pagination testing.
     * Creates at least 3 exercises so we can navigate to a "second page".
     */
    fun createTestExerciseSets(
        workout: String = TEST_WORKOUT,
        day: String = TEST_DAY,
        count: Int = 3
    ): List<RoomExerciseSet> {
        return (1..count).map { index ->
            RoomExerciseSet(
                workout = workout,
                day = day,
                step = "$index",
                primaryStep = index,
                superSetStep = null,
                alternateStep = null,
                name = "TestCategory_TestExercise$index", // Format: Category_ExerciseName, where ExerciseName matches Exercise.id
                note = "Test exercise number $index",
                reps = 10,
                sets = 3,
                isToFailure = false,
                rest = 1, // Short rest time to avoid infinite recomposition in tests
                repsUnit = "reps",
                repsRange = 0,
                timeLimit = null,
                timeLimitUnit = null,
                repsSequence = emptyList()
            )
        }
    }

    /**
     * Creates matching RoomExercise entities for the given exercise sets.
     * These are needed for the ExerciseSet.exercise Flow to resolve.
     */
    fun createTestExercises(
        workout: String = TEST_WORKOUT,
        count: Int = 3
    ): List<RoomExercise> {
        return (1..count).map { index ->
            RoomExercise(
                workout = workout,
                id = "TestCategory_TestExercise$index", // Must match the exercise name after underscore
                description = "Test exercise $index description"
            )
        }
    }

    /**
     * Creates a test RoomWorkoutPlan.
     */
    fun createTestWorkoutPlan(
        workout: String = TEST_WORKOUT,
        totalDays: Int = 84,
        lastViewedDay: Int = 1
    ): RoomWorkoutPlan {
        return RoomWorkoutPlan(
            workout = workout,
            totalDays = totalDays,
            lastViewedDay = lastViewedDay,
            workoutStartDate = Instant.now(),
            restDays = emptyList(),
            description = "Test workout plan",
            globalAlternateLabels = emptyList(),
            globalAlternate = null
        )
    }

    /**
     * Creates a test set record for a given exercise.
     */
    fun createTestSetRecord(
        workout: String = TEST_WORKOUT,
        day: String = TEST_DAY,
        step: String,
        exerciseName: String,
        weight: Double = 100.0,
        reps: Int = 10,
        completed: Instant = Instant.now()
    ): SetRecord {
        val targetSet = RoomExerciseSet(
            workout = workout,
            day = day,
            step = step,
            primaryStep = step.toIntOrNull() ?: 1,
            superSetStep = null,
            alternateStep = null,
            name = exerciseName,
            note = "",
            reps = reps,
            sets = 3,
            isToFailure = false,
            rest = 60,
            repsUnit = "reps",
            repsRange = 0,
            timeLimit = null,
            timeLimitUnit = null,
            repsSequence = emptyList()
        )

        val targetSetId = "$day.$step"
        return SetRecord(
            weight = weight,
            reps = reps,
            workout = workout,
            targetSet = targetSetId,
            completed = completed,
            exercise = exerciseName
        )
    }
}
