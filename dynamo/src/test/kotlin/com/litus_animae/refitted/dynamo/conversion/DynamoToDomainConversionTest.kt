package com.litus_animae.refitted.dynamo.conversion

import com.litus_animae.refitted.dynamo.entities.DynamoExerciseSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DynamoDB to Domain Conversion")
class DynamoToDomainConversionTest {

    @Test
    @DisplayName("toDomain converts all fields correctly")
    fun toDomain_mapsAllFields() = runBlocking {
        // Given
        val dynamoSet = DynamoExerciseSet(
            id = "1.2",
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
            repsSequence = "6,7,8"
        )

        // When
        val domainModel = dynamoSet.toDomain()

        // Then
        assertEquals("w", domainModel.workout)
        assertEquals("1", domainModel.day)
        assertEquals("2", domainModel.step)
        assertEquals("n", domainModel.name)
        assertEquals("note", domainModel.note)
        assertEquals(1, domainModel.reps)
        assertEquals(2, domainModel.sets)
        assertTrue(domainModel.isToFailure)
        assertEquals(3, domainModel.rest)
        assertEquals("reps", domainModel.repsUnit)
        assertEquals(4, domainModel.repsRange)
        assertEquals(5, domainModel.timeLimit)
        assertEquals("seconds", domainModel.timeLimitUnit)
        assertEquals(listOf(6, 7, 8), domainModel.repsSequence)
        assertNull(domainModel.exercise.first())
    }

    @Test
    @DisplayName("toDomain handles alternate step notation")
    fun toDomain_handlesAlternateStep() {
        // Given
        val dynamoSet = DynamoExerciseSet(id = "1.2.a")

        // When
        val domainModel = dynamoSet.toDomain()

        // Then
        assertEquals("1", domainModel.day)
        assertEquals("2.a", domainModel.step)
    }

    @Test
    @DisplayName("toDomain handles superset step notation")
    fun toDomain_handlesSupersetStep() {
        // Given
        val dynamoSet = DynamoExerciseSet(id = "1.2.3")

        // When
        val domainModel = dynamoSet.toDomain()

        // Then
        assertEquals("1", domainModel.day)
        assertEquals("2.3", domainModel.step)
    }

    @Test
    @DisplayName("toDomain filters invalid reps sequence values")
    fun toDomain_handlesInvalidRepsSequence() {
        // Given
        val dynamoSet = DynamoExerciseSet(repsSequence = "1,a,2,b,3")

        // When
        val domainModel = dynamoSet.toDomain()

        // Then
        assertEquals(listOf(1, 2, 3), domainModel.repsSequence)
    }

    @Test
    @DisplayName("toDomain handles empty reps sequence")
    fun toDomain_handlesEmptyRepsSequence() {
        // Given
        val dynamoSet = DynamoExerciseSet(repsSequence = "")

        // When
        val domainModel = dynamoSet.toDomain()

        // Then
        assertEquals(emptyList<Int>(), domainModel.repsSequence)
    }

    @Test
    @DisplayName("toDomain handles invalid ID format")
    fun toDomain_handlesInvalidId() {
        // Given
        val dynamoSet = DynamoExerciseSet(id = "invalid")

        // When
        val domainModel = dynamoSet.toDomain()

        // Then
        assertEquals("invalid", domainModel.day)
        assertEquals("", domainModel.step)
    }
}
