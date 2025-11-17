package com.litus_animae.refitted.data.conversion

import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.dynamo.entities.DynamoExerciseSet
import com.litus_animae.refitted.room.entities.RoomExerciseSet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the conversion from DynamoExerciseSet (network) → ExerciseSet (domain) → RoomExerciseSet (Room entity)
 */
@DisplayName("DynamoDB to Room Conversion")
internal class DynamoToRoomConversionTest {

  @Test
  @DisplayName("fromDomain converts all fields correctly")
  fun fromDomain_mapsAllFields() {
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

    // When: Convert through domain model
    val domainModel = dynamoSet.toDomain()
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
    val dynamoSet = DynamoExerciseSet(id = "1.2.a")

    // When
    val domainModel = dynamoSet.toDomain()
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
    val dynamoSet = DynamoExerciseSet(id = "1.2.3")

    // When
    val domainModel = dynamoSet.toDomain()
    val roomSet = RoomExerciseSet.fromDomain(domainModel)

    // Then
    assertEquals("1", roomSet.day)
    assertEquals("2.3", roomSet.step)
    assertNull(roomSet.alternateStep)
    assertEquals(3, roomSet.superSetStep)
  }

  @Test
  @DisplayName("fromDomain filters invalid reps sequence values")
  fun fromDomain_handlesInvalidRepsSequence() {
    // Given
    val dynamoSet = DynamoExerciseSet(repsSequence = "1,a,2,b,3")

    // When
    val domainModel = dynamoSet.toDomain()
    val roomSet = RoomExerciseSet.fromDomain(domainModel)

    // Then
    assertEquals(listOf(1, 2, 3), roomSet.repsSequence)
  }

  @Test
  @DisplayName("fromDomain handles empty reps sequence")
  fun fromDomain_handlesEmptyRepsSequence() {
    // Given
    val dynamoSet = DynamoExerciseSet(repsSequence = "")

    // When
    val domainModel = dynamoSet.toDomain()
    val roomSet = RoomExerciseSet.fromDomain(domainModel)

    // Then
    assertEquals(emptyList<Int>(), roomSet.repsSequence)
  }

  @Test
  @DisplayName("fromDomain handles invalid ID format")
  fun fromDomain_handlesInvalidId() {
    // Given
    val dynamoSet = DynamoExerciseSet(id = "invalid")

    // When
    val domainModel = dynamoSet.toDomain()
    val roomSet = RoomExerciseSet.fromDomain(domainModel)

    // Then
    assertEquals("invalid", roomSet.day)
    assertEquals("", roomSet.step)
    assertEquals(0, roomSet.primaryStep)
    assertNull(roomSet.superSetStep)
    assertNull(roomSet.alternateStep)
  }
}
