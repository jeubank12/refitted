package com.litus_animae.refitted.room.conversion

import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Room from Domain Conversion")
internal class RoomFromDomainConversionTest {

    @Test
    @DisplayName("fromDomain converts all fields correctly")
    fun fromDomain_mapsAllFields() {
        // Given
        val domainModel = ExerciseSet(
            day = "1",
            step = "2",
            workout = "w",
            name = "n",
            note = "note",
            reps = 1,
            sets = 2,
            isToFailure = true,
            rest = 3,
            repsUnit = "reps",
            repsRange = 4,
            timeLimit = 5,
            timeLimitUnit = "seconds",
            repsSequence = listOf(6, 7, 8),
            exercise = flowOf(null)
        )

        // When
        val roomSet = RoomExerciseSet.fromDomain(domainModel)

        // Then
        assertEquals("w", roomSet.workout)
        assertEquals("1", roomSet.day)
        assertEquals("2", roomSet.step)
        assertEquals(2, roomSet.primaryStep)
        assertNull(roomSet.superSetStep)
        assertNull(roomSet.alternateStep)
        assertEquals("n", roomSet.name)
        assertEquals("note", roomSet.note)
        assertEquals(1, roomSet.reps)
        assertEquals(2, roomSet.sets)
        assertTrue(roomSet.isToFailure)
        assertEquals(3, roomSet.rest)
        assertEquals("reps", roomSet.repsUnit)
        assertEquals(4, roomSet.repsRange)
        assertEquals(5, roomSet.timeLimit)
        assertEquals("seconds", roomSet.timeLimitUnit)
        assertEquals(listOf(6, 7, 8), roomSet.repsSequence)
    }

    @Test
    @DisplayName("fromDomain handles alternate step notation")
    fun fromDomain_handlesAlternateStep() {
        // Given
        val domainModel = ExerciseSet(
            day = "1",
            step = "2.a",
            workout = "",
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

        // When
        val roomSet = RoomExerciseSet.fromDomain(domainModel)

        // Then
        assertEquals("1", roomSet.day)
        assertEquals("2.a", roomSet.step)
        assertEquals("a", roomSet.alternateStep)
        assertNull(roomSet.superSetStep)
    }

    @Test
    @DisplayName("fromDomain handles superset step notation")
    fun fromDomain_handlesSupersetStep() {
        // Given
        val domainModel = ExerciseSet(
            day = "1",
            step = "2.3",
            workout = "",
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

        // When
        val roomSet = RoomExerciseSet.fromDomain(domainModel)

        // Then
        assertEquals("1", roomSet.day)
        assertEquals("2.3", roomSet.step)
        assertNull(roomSet.alternateStep)
        assertEquals(3, roomSet.superSetStep)
    }

    @Test
    @DisplayName("fromDomain handles invalid ID format")
    fun fromDomain_handlesInvalidId() {
        // Given
        val domainModel = ExerciseSet(
            day = "invalid",
            step = "",
            workout = "",
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

        // When
        val roomSet = RoomExerciseSet.fromDomain(domainModel)

        // Then
        assertEquals("invalid", roomSet.day)
        assertEquals("", roomSet.step)
        assertEquals(0, roomSet.primaryStep)
        assertNull(roomSet.superSetStep)
        assertNull(roomSet.alternateStep)
    }
}
