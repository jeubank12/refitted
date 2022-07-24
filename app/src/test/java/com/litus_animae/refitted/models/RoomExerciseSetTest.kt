package com.litus_animae.refitted.models

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
}