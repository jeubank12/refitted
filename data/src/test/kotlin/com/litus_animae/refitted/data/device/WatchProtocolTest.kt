package com.litus_animae.refitted.data.device

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WatchProtocolTest {

  private val basicExercise = WatchExercise(
    name = "Bench Press",
    sets = 3,
    reps = 10,
    restSeconds = 60,
    suggestedWeight = 135.0,
    isToFailure = false,
    repsSequence = emptyList(),
    timeLimitMillis = null
  )

  @Nested
  @DisplayName("PLAN encode/decode round-trip")
  inner class PlanRoundTrip {
    @Test
    fun `basic exercise round-trips`() {
      val encoded = WatchProtocol.encodePlan("Push Day", "1", listOf(basicExercise))

      val decoded = WatchProtocol.decode(encoded)

      assertThat(decoded).isEqualTo(WatchProtocol.Plan("Push Day", "1", listOf(basicExercise)))
    }

    @Test
    fun `sets = -1 for an open challenge set round-trips`() {
      val exercise = basicExercise.copy(sets = -1)

      val decoded = WatchProtocol.decode(WatchProtocol.encodePlan("Push Day", "1", listOf(exercise)))

      assertThat(decoded).isEqualTo(WatchProtocol.Plan("Push Day", "1", listOf(exercise)))
    }

    @Test
    fun `repsSequence round-trips`() {
      val exercise = basicExercise.copy(repsSequence = listOf(12, 10, 8, 6))

      val decoded = WatchProtocol.decode(WatchProtocol.encodePlan("Push Day", "1", listOf(exercise)))

      assertThat(decoded).isEqualTo(WatchProtocol.Plan("Push Day", "1", listOf(exercise)))
    }

    @Test
    fun `isToFailure round-trips`() {
      val exercise = basicExercise.copy(isToFailure = true)

      val decoded = WatchProtocol.decode(WatchProtocol.encodePlan("Push Day", "1", listOf(exercise)))

      assertThat(decoded).isEqualTo(WatchProtocol.Plan("Push Day", "1", listOf(exercise)))
    }

    @Test
    fun `timeLimitMillis present round-trips`() {
      val exercise = basicExercise.copy(timeLimitMillis = 45000)

      val decoded = WatchProtocol.decode(WatchProtocol.encodePlan("Push Day", "1", listOf(exercise)))

      assertThat(decoded).isEqualTo(WatchProtocol.Plan("Push Day", "1", listOf(exercise)))
    }

    @Test
    fun `timeLimitMillis absent round-trips as null`() {
      val decoded = WatchProtocol.decode(WatchProtocol.encodePlan("Push Day", "1", listOf(basicExercise)))

      assertThat((decoded as WatchProtocol.Plan).exercises.single().timeLimitMillis).isNull()
    }

    @Test
    fun `all optional fields together round-trip in order`() {
      val exercise = basicExercise.copy(
        repsSequence = listOf(15, 12, 10),
        isToFailure = true,
        timeLimitMillis = 30000
      )

      val decoded = WatchProtocol.decode(WatchProtocol.encodePlan("Push Day", "1", listOf(exercise)))

      assertThat(decoded).isEqualTo(WatchProtocol.Plan("Push Day", "1", listOf(exercise)))
    }
  }

  @Nested
  @DisplayName("Other message round-trips")
  inner class OtherMessages {
    @Test
    fun `ACK round-trips`() {
      assertThat(WatchProtocol.decode(WatchProtocol.encodeAck(7))).isEqualTo(WatchProtocol.Ack(7))
    }

    @Test
    fun `END round-trips`() {
      assertThat(WatchProtocol.decode(WatchProtocol.encodeEnd())).isEqualTo(WatchProtocol.End)
    }

    @Test
    fun `NAK round-trips`() {
      assertThat(WatchProtocol.decode(WatchProtocol.encodeNak("bad version")))
        .isEqualTo(WatchProtocol.Nak("bad version"))
    }

    @Test
    fun `SET_DONE round-trips`() {
      val setDone = WatchProtocol.SetDone(
        seq = 3,
        exerciseIndex = 1,
        setNumber = 2,
        reps = 8,
        weightCenti = 13500,
        elapsedMs = 120_000L
      )

      assertThat(WatchProtocol.decode(WatchProtocol.encodeSetDone(setDone))).isEqualTo(setDone)
    }
  }

  @Nested
  @DisplayName("Version rejection")
  inner class VersionRejection {
    @Test
    fun `a newer protocol version than we understand is rejected, not misparsed`() {
      val fromTheFuture = listOf(WatchProtocol.PROTOCOL_VERSION + 1, 1, listOf("x", "1", emptyList<Any>()))

      val decoded = WatchProtocol.decode(fromTheFuture)

      assertThat(decoded).isEqualTo(WatchProtocol.UnsupportedVersion(WatchProtocol.PROTOCOL_VERSION + 1))
    }
  }
}
