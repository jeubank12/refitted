package com.litus_animae.refitted.data.device

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class WatchSessionTest {

  private val benchPress = WatchExercise(
    name = "Bench Press",
    sets = 3,
    reps = 10,
    restSeconds = 60,
    suggestedWeight = 135.0,
    isToFailure = false,
    repsSequence = emptyList(),
    timeLimitMillis = null
  )

  private val squat = WatchExercise(
    name = "Squat",
    sets = 3,
    reps = 5,
    restSeconds = 90,
    suggestedWeight = 225.0,
    isToFailure = false,
    repsSequence = emptyList(),
    timeLimitMillis = null
  )

  private val sessionStart: Instant = Instant.parse("2026-08-07T12:00:00Z")
  private val session = WatchSessionState(
    workout = "PushDay",
    startInstant = sessionStart,
    plan = WatchPlan(
      workout = "PushDay",
      day = "1",
      exercises = listOf(benchPress, squat),
      ids = listOf("1.1", "1.2")
    )
  )

  @Nested
  @DisplayName("SetDone -> SetRecord")
  inner class ToSetRecord {
    @Test
    fun `resolves exerciseIndex to the matching exercise's id and name`() {
      val setDone = WatchProtocol.SetDone(
        seq = 1,
        exerciseIndex = 1,
        setNumber = 1,
        reps = 5,
        weightCenti = 22500,
        elapsedMs = 30_000L
      )

      val record = setDone.toSetRecord(session)

      assertThat(record?.targetSet).isEqualTo("1.2")
      assertThat(record?.exercise).isEqualTo(squat.name)
    }

    @Test
    fun `weightCenti divides down to weight`() {
      val setDone = WatchProtocol.SetDone(0, 0, 1, 10, weightCenti = 13500, elapsedMs = 0L)

      assertThat(setDone.toSetRecord(session)?.weight).isEqualTo(135.0)
    }

    @Test
    fun `elapsedMs is added onto the session's start instant`() {
      val setDone = WatchProtocol.SetDone(0, 0, 1, 10, weightCenti = 13500, elapsedMs = 45_000L)

      assertThat(setDone.toSetRecord(session)?.completed).isEqualTo(sessionStart.plusMillis(45_000L))
    }

    @Test
    fun `replaying the same completion produces a byte-identical record`() {
      val setDone = WatchProtocol.SetDone(0, 0, 1, 10, weightCenti = 13500, elapsedMs = 45_000L)

      val first = setDone.toSetRecord(session)
      val replay = setDone.toSetRecord(session)

      assertThat(first).isEqualTo(replay)
    }

    @Test
    fun `an exerciseIndex outside the session's plan resolves to null instead of throwing`() {
      val setDone = WatchProtocol.SetDone(0, exerciseIndex = 5, setNumber = 1, reps = 10, weightCenti = 13500, elapsedMs = 0L)

      assertThat(setDone.toSetRecord(session)).isNull()
    }
  }
}
