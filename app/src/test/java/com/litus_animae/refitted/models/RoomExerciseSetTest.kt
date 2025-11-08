package com.litus_animae.refitted.models

import com.litus_animae.refitted.models.dynamo.MutableExerciseSet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class RoomExerciseSetTest {
  @Test
  internal fun parseSimplePrimaryStep() {
    val pStep = RoomExerciseSet.parsePrimaryStep("2.1")
    assertEquals(1, pStep)
  }
  @Test
  internal fun parsePrimaryStepFromSuperSet() {
    val pStep = RoomExerciseSet.parsePrimaryStep("3.2.1")
    assertEquals(2, pStep)
  }
  @Test
  internal fun parsePrimaryStepFromAlternateSet() {
    val pStep = RoomExerciseSet.parsePrimaryStep("3.2.a")
    assertEquals(2, pStep)
  }

  @Test
  internal fun parsePrimaryStep_returnsZeroForInvalidInput() {
    assertEquals(0, RoomExerciseSet.parsePrimaryStep("invalid"))
    assertEquals(0, RoomExerciseSet.parsePrimaryStep("1"))
    assertEquals(0, RoomExerciseSet.parsePrimaryStep(""))
  }

  @Test
  internal fun parseNoSuperSetFromSimple() {
    val sStep = RoomExerciseSet.parseSuperSetStep("3.2")
    assertNull(sStep)
  }
  @Test
  internal fun parseNoSuperSetFromAlternate() {
    val sStep = RoomExerciseSet.parseSuperSetStep("3.2.a")
    assertNull(sStep)
  }
  @Test
  internal fun parseSuperSet() {
    val sStep = RoomExerciseSet.parseSuperSetStep("3.2.1")
    assertEquals(1, sStep)
  }
  @Test
  internal fun parseSuperSetWithAlternate() {
    val sStep = RoomExerciseSet.parseSuperSetStep("3.2.1.a")
    assertEquals(1, sStep)
  }

  @Test
  internal fun parseNoAltSetFromSimple() {
    val sStep = RoomExerciseSet.parseAlternateStep("3.2")
    assertNull(sStep)
  }
  @Test
  internal fun parseNoAltSetFromSuper() {
    val sStep = RoomExerciseSet.parseAlternateStep("3.2.1")
    assertNull(sStep)
  }
  @Test
  internal fun parseAltSet() {
    val sStep = RoomExerciseSet.parseAlternateStep("3.2.a")
    assertEquals("a", sStep)
  }
  @Test
  internal fun parseAltSetWithSuper() {
    val sStep = RoomExerciseSet.parseAlternateStep("3.2.1.a")
    assertEquals("a", sStep)
  }

  @Test
  internal fun constructor_mapsAllFields() {
    // Given
    val mutable = MutableExerciseSet(
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
    val subject = RoomExerciseSet(mutable)

    // Then
    assertEquals("w", subject.workout)
    assertEquals("1", subject.day)
    assertEquals("2", subject.step)
    assertEquals(2, subject.primaryStep)
    assertNull(subject.superSetStep)
    assertNull(subject.alternateStep)
    assertEquals("n", subject.name)
    assertEquals("note", subject.note)
    assertEquals(1, subject.reps)
    assertEquals(2, subject.sets)
    assertTrue(subject.isToFailure)
    assertEquals(3, subject.rest)
    assertEquals("reps", subject.repsUnit)
    assertEquals(4, subject.repsRange)
    assertEquals(5, subject.timeLimit)
    assertEquals("seconds", subject.timeLimitUnit)
    assertEquals(listOf(6, 7, 8), subject.repsSequence)
  }

  @Test
  internal fun constructor_handlesAlternateStep() {
    // Given
    val mutable = MutableExerciseSet(id = "1.2.a")

    // When
    val subject = RoomExerciseSet(mutable)

    // Then
    assertEquals("1", subject.day)
    assertEquals("2.a", subject.step)
    assertEquals("a", subject.alternateStep)
    assertNull(subject.superSetStep)
  }

  @Test
  internal fun constructor_handlesSupersetStep() {
    // Given
    val mutable = MutableExerciseSet(id = "1.2.3")

    // When
    val subject = RoomExerciseSet(mutable)

    // Then
    assertEquals("1", subject.day)
    assertEquals("2.3", subject.step)
    assertNull(subject.alternateStep)
    assertEquals(3, subject.superSetStep)
  }

  @Test
  internal fun constructor_handlesInvalidRepsSequence() {
    // Given
    val mutable = MutableExerciseSet(repsSequence = "1,a,2,b,3")

    // When
    val subject = RoomExerciseSet(mutable)

    // Then
    assertEquals(listOf(1, 2, 3), subject.repsSequence)
  }

  @Test
  internal fun constructor_handlesEmptyRepsSequence() {
    // Given
    val mutable = MutableExerciseSet(repsSequence = "")

    // When
    val subject = RoomExerciseSet(mutable)

    // Then
    assertEquals(emptyList<Int>(), subject.repsSequence)
  }

  @Test
  internal fun constructor_handlesInvalidId() {
    // Given
    val mutable = MutableExerciseSet(id = "invalid")

    // When
    val subject = RoomExerciseSet(mutable)

    // Then
    assertEquals("invalid", subject.day)
    assertEquals("", subject.step)
    assertEquals(0, subject.primaryStep)
    assertNull(subject.superSetStep)
    assertNull(subject.alternateStep)
  }
}
