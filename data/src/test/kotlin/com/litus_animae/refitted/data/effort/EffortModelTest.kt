package com.litus_animae.refitted.data.effort

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private val BASE: Instant = Instant.parse("2024-01-01T12:00:00Z")
private fun day(n: Long): Instant = BASE.plus(Duration.ofDays(n))

class EffortModelTest {

  @Nested
  @DisplayName("capacity")
  inner class Capacity {
    @Test
    fun `matches the spec's worked examples`() {
      assertThat(EffortModel.capacity(80.0, 15)).isWithin(1e-9).of(120.0)
      assertThat(EffortModel.capacity(90.0, 15)).isWithin(1e-9).of(135.0)
      assertThat(EffortModel.capacity(130.0, 3)).isWithin(1e-9).of(143.0)
      assertThat(EffortModel.capacity(100.0, 2)).isWithin(1e-6).of(106.666666)
      assertThat(EffortModel.capacity(100.0, 10)).isWithin(1e-6).of(133.333333)
      assertThat(EffortModel.capacity(100.0, 12)).isWithin(1e-9).of(140.0)
      assertThat(EffortModel.capacity(180.0, 5)).isWithin(1e-9).of(210.0)
    }

    @Test
    fun `clamps reps at the configured cap`() {
      assertThat(EffortModel.capacity(100.0, 20)).isWithin(1e-9)
        .of(EffortModel.capacity(100.0, 15))
    }

    @Test
    fun `zero reps demonstrates only the weight`() {
      assertThat(EffortModel.capacity(100.0, 0)).isWithin(1e-9).of(100.0)
    }

    @Test
    fun `negative weight floors at the same minimum as bodyweight`() {
      assertThat(EffortModel.capacity(-10.0, 8))
        .isWithin(1e-9).of(EffortModel.capacity(0.0, 8))
    }

    @Test
    fun `bodyweight (zero weight) capacity still tracks reps instead of collapsing to zero`() {
      val fewerReps = EffortModel.capacity(0.0, 5)
      val moreReps = EffortModel.capacity(0.0, 12)

      assertThat(fewerReps).isGreaterThan(0.0)
      assertThat(moreReps).isGreaterThan(fewerReps)
    }

    @Test
    fun `the bodyweight floor stops applying once real weight is logged`() {
      assertThat(EffortModel.capacity(5.0, 8)).isGreaterThan(EffortModel.capacity(0.0, 8))
    }
  }

  @Nested
  @DisplayName("bubbleSize")
  inner class Hump {
    @Test
    fun `hits every anchor exactly`() {
      assertThat(EffortModel.bubbleSize(-2.0)).isEqualTo(0.00f)
      assertThat(EffortModel.bubbleSize(-1.0)).isEqualTo(0.30f)
      assertThat(EffortModel.bubbleSize(0.0)).isEqualTo(0.60f)
      assertThat(EffortModel.bubbleSize(0.5)).isEqualTo(0.85f)
      assertThat(EffortModel.bubbleSize(1.0)).isEqualTo(1.00f)
      assertThat(EffortModel.bubbleSize(1.5)).isEqualTo(0.90f)
      assertThat(EffortModel.bubbleSize(2.5)).isEqualTo(0.40f)
    }

    @Test
    fun `clamps flat below -2 and above 2_5`() {
      assertThat(EffortModel.bubbleSize(-5.0)).isEqualTo(0.00f)
      assertThat(EffortModel.bubbleSize(50.0)).isEqualTo(0.40f)
    }

    @Test
    fun `rises monotonically from -2 to the peak at +1`() {
      val samples = generateSequence(-2.0) { it + 0.1 }.takeWhile { it <= 1.0 }.toList()
      val sizes = samples.map { EffortModel.bubbleSize(it) }
      assertThat(sizes).isInOrder()
    }

    @Test
    fun `falls monotonically from the peak at +1 to +2_5`() {
      val samples = generateSequence(1.0) { it + 0.1 }.takeWhile { it <= 2.5 }.toList()
      val sizes = samples.map { EffortModel.bubbleSize(it) }
      assertThat(sizes).isInOrder(compareByDescending<Float> { it })
    }

    @Test
    fun `null z is neutral, not zero`() {
      assertThat(EffortModel.bubbleSize(null)).isEqualTo(EffortModel.NEUTRAL_SIZE)
    }
  }

  @Nested
  @DisplayName("worked examples (spec table, curve at ĉ=133, s=10)")
  inner class WorkedExamples {
    private fun sizeFor(weight: Double, reps: Int): Float {
      val c = EffortModel.capacity(weight, reps)
      val z = (c - 133.0) / 10.0
      return EffortModel.bubbleSize(z)
    }

    @Test
    fun `80 lb x 15 - somewhat below - smallish`() {
      assertThat(sizeFor(80.0, 15).toDouble()).isWithin(1e-3).of(0.21)
    }

    @Test
    fun `90 lb x 15 - slightly above - larger than on-curve`() {
      val size = sizeFor(90.0, 15)
      assertThat(size.toDouble()).isWithin(1e-3).of(0.70)
      assertThat(size).isGreaterThan(EffortModel.bubbleSize(0.0))
    }

    @Test
    fun `130 lb x 3 - above - peak`() {
      assertThat(sizeFor(130.0, 3)).isEqualTo(1.00f)
    }

    @Test
    fun `100 lb x 2 - well below - minimum`() {
      assertThat(sizeFor(100.0, 2)).isEqualTo(0.00f)
    }

    @Test
    fun `100 lb x 10 - on-just above - large`() {
      assertThat(sizeFor(100.0, 10).toDouble()).isWithin(1e-3).of(0.617)
    }

    @Test
    fun `100 lb x 12 - on-just above - near peak`() {
      assertThat(sizeFor(100.0, 12).toDouble()).isWithin(1e-3).of(0.91)
    }

    @Test
    fun `180 lb x 5 - far above - punished`() {
      assertThat(sizeFor(180.0, 5)).isEqualTo(0.40f)
    }

    @Test
    fun `ordering matches the spec table`() {
      val below = sizeFor(100.0, 2)
      val somewhatBelow = sizeFor(80.0, 15)
      val onCurve = EffortModel.bubbleSize(0.0)
      val slightlyAbove = sizeFor(90.0, 15)
      val peakA = sizeFor(130.0, 3)
      val peakB = sizeFor(100.0, 12)
      val punished = sizeFor(180.0, 5)

      assertThat(below).isLessThan(somewhatBelow)
      assertThat(somewhatBelow).isLessThan(onCurve)
      assertThat(onCurve).isLessThan(slightlyAbove)
      assertThat(slightlyAbove).isLessThan(peakB)
      assertThat(peakB).isLessThan(peakA)
      assertThat(punished).isLessThan(slightlyAbove)
    }
  }

  @Nested
  @DisplayName("session aggregation")
  inner class SessionAggregation {
    @Test
    fun `a warm-up set does not change the fit driven by the session best`() {
      val topOnly = (0..2).map { EffortSet(day(it * 7L), 100.0 + it * 2, 5) }
      val withWarmup = topOnly.dropLast(1) +
        listOf(EffortSet(day(14), 40.0, 15), EffortSet(day(14), 104.0, 5))
      val probe = EffortSet(day(28), 110.0, 5)

      val trendTop = EffortModel.score(topOnly + probe).trend.last()
      val trendWarm = EffortModel.score(withWarmup + probe).trend.last()

      assertThat(trendWarm.expectedCapacity).isWithin(1e-9).of(trendTop.expectedCapacity)
    }

    @Test
    fun `sets sharing a session share one expectation`() {
      val priors = (0..2).map { EffortSet(day(it * 7L), 100.0, 8) }
      val session = listOf(EffortSet(day(21), 40.0, 15), EffortSet(day(21), 105.0, 8))
      val series = EffortModel.score(priors + session)

      val scored = series.sets.filter { it.sessionIndex == 3 }
      assertThat(scored).hasSize(2)
      assertThat(scored.map { it.expectation }.distinct()).hasSize(1)
      assertThat(scored[0].expectation).isNotNull()
    }

    @Test
    fun `the session zone determines which calendar day a set falls on`() {
      val early = Instant.parse("2024-01-01T02:00:00Z")
      val late = Instant.parse("2024-01-01T10:00:00Z")
      val sets = listOf(EffortSet(early, 100.0, 5), EffortSet(late, 100.0, 5))

      val utc = EffortModel.score(sets, zone = ZoneId.of("UTC"))
      val chicago = EffortModel.score(sets, zone = ZoneId.of("America/Chicago"))

      assertThat(utc.sets.map { it.sessionIndex }.distinct()).hasSize(1)
      assertThat(chicago.sets.map { it.sessionIndex }.distinct()).hasSize(2)
    }
  }

  @Nested
  @DisplayName("cold start")
  inner class ColdStart {
    @Test
    fun `fewer than 3 prior sessions predicts nothing`() {
      val sets = (0..2).map { EffortSet(day(it * 7L), 100.0, 8) }
      val series = EffortModel.score(sets)

      assertThat(series.trend).isEmpty()
      assertThat(series.sets).hasSize(3)
      series.sets.forEach {
        assertThat(it.expectation).isNull()
        assertThat(it.z).isNull()
        assertThat(it.size).isEqualTo(EffortModel.NEUTRAL_SIZE)
        assertThat(it.zone).isEqualTo(EffortZone.COLD)
      }
    }

    @Test
    fun `the 4th session is the first with a prediction`() {
      val sets = (0..2).map { EffortSet(day(it * 7L), 100.0, 8) } +
        EffortSet(day(21), 105.0, 8)
      val series = EffortModel.score(sets)

      assertThat(series.trend).hasSize(1)
      assertThat(series.trend.single().sessionIndex).isEqualTo(3)
      val fourth = series.sets.last()
      assertThat(fourth.expectation).isNotNull()
      assertThat(fourth.zone).isNotEqualTo(EffortZone.COLD)
    }
  }

  @Nested
  @DisplayName("causality")
  inner class Causality {
    @Test
    fun `a session's own outcome never changes its own or earlier expectations`() {
      val ramp = (0..4).map { EffortSet(day(it * 7L), 100.0 + it * 2, 8) }
      val hugePr = EffortSet(day(35), 250.0, 8)

      val without = EffortModel.score(ramp)
      val with = EffortModel.score(ramp + hugePr)

      val trendWithout = without.trend.associateBy { it.sessionIndex }
      val trendWithPrior = with.trend.filter { it.sessionIndex < 5 }.associateBy { it.sessionIndex }

      assertThat(trendWithPrior.keys).isEqualTo(trendWithout.keys)
      trendWithout.forEach { (idx, tp) ->
        assertThat(trendWithPrior.getValue(idx).expectedCapacity).isWithin(1e-9).of(tp.expectedCapacity)
      }
    }
  }

  @Nested
  @DisplayName("recency")
  inner class Recency {
    @Test
    fun `a long plateau after a ramp pulls the expectation down to the plateau`() {
      val ramp = (0..4).map { i -> EffortSet(day(i * 7L), 100.0 + i * 5, 0) }
      val plateau = (0 until 15).map { i -> EffortSet(day(35L + i * 7L), 120.0, 0) }
      val series = EffortModel.score(ramp + plateau)

      val last = series.trend.last()
      assertThat(last.expectedCapacity).isWithin(5.0).of(120.0)
    }
  }

  @Nested
  @DisplayName("residual scale")
  inner class ResidualScale {
    @Test
    fun `a flat history floors s at the configured fraction of the expectation`() {
      val c = 100.0
      val priors = (0..2).map { EffortSet(day(it * 7L), c, 0) }
      val onCurve = EffortSet(day(21), c, 0)
      val probe = EffortSet(day(21), c * 1.05, 0)
      val series = EffortModel.score(priors + listOf(onCurve, probe))

      val session3 = series.sets.filter { it.sessionIndex == 3 }
      val onCurveScored = session3.first { it.source.weight == c }
      val probeScored = session3.first { it.source.weight == c * 1.05 }

      assertThat(onCurveScored.z!!).isWithin(1e-6).of(0.0)
      assertThat(onCurveScored.size.toDouble()).isWithin(1e-3).of(0.60)
      assertThat(probeScored.z!!).isWithin(1e-6).of(1.0)
      assertThat(probeScored.size).isEqualTo(1.00f)
    }
  }

  @Nested
  @DisplayName("trend")
  inner class Trend {
    @Test
    fun `expectedWeight at typicalReps reconstructs expectedCapacity`() {
      val sets = (0 until 10).map { i -> EffortSet(day(i * 3L), 80.0 + i * 3, 5 + i % 4) }
      val series = EffortModel.score(sets)

      assertThat(series.trend).isNotEmpty()
      series.trend.forEach { tp ->
        val reconstructed = tp.expectedWeight * (1 + tp.typicalReps / EffortConfig.Default.epleyDivisor)
        assertThat(reconstructed).isWithin(1e-6).of(tp.expectedCapacity)
      }
    }

    @Test
    fun `typical reps tracks the top set, ignoring warm-up reps`() {
      val sets = (0 until 6).flatMap { i ->
        listOf(
          EffortSet(day(i * 7L), 50.0, 15),
          EffortSet(day(i * 7L), 100.0, 5)
        )
      }
      val series = EffortModel.score(sets)

      assertThat(series.trend).isNotEmpty()
      series.trend.forEach { tp -> assertThat(tp.typicalReps).isWithin(1e-6).of(5.0) }
    }

    @Test
    fun `a long layoff does not blow up the expectation`() {
      val priors = (0..3).map { EffortSet(day(it * 7L), 100.0 + it * 10, 8) }
      val afterLayoff = EffortSet(day(221), 100.0, 8)
      val series = EffortModel.score(priors + afterLayoff)

      val last = series.trend.last()
      assertThat(last.expectedCapacity).isFinite()
      assertThat(last.expectedCapacity).isGreaterThan(0.0)
      assertThat(last.expectedCapacity).isLessThan(500.0)
    }
  }

  @Nested
  @DisplayName("scoreWithBootstrap")
  inner class Bootstrap {
    @Test
    fun `a first-ever session is all COLD no matter how many sets it has`() {
      val firstSession = (0 until 6).map { EffortSet(day(0).plusSeconds(it * 60L), 100.0, 8) }
      val series = EffortModel.scoreWithBootstrap(firstSession)

      assertThat(series.trend).isEmpty()
      series.sets.forEach {
        assertThat(it.expectationSource).isNull()
        assertThat(it.zone).isEqualTo(EffortZone.COLD)
      }
    }

    @Test
    fun `a second session bootstraps once 3 prior sets exist`() {
      val firstSession = (0 until 3).map { EffortSet(day(0).plusSeconds(it * 60L), 100.0, 8) }
      val secondSession = listOf(EffortSet(day(7), 105.0, 8))
      val series = EffortModel.scoreWithBootstrap(firstSession + secondSession)

      val second = series.sets.last()
      assertThat(second.expectationSource).isEqualTo(ExpectationSource.BOOTSTRAP)
      assertThat(second.expectation).isNotNull()
      assertThat(second.zone).isNotEqualTo(EffortZone.COLD)
      assertThat(series.trend).hasSize(1)
      assertThat(series.trend.single().expectationSource).isEqualTo(ExpectationSource.BOOTSTRAP)
    }

    @Test
    fun `a second session with too few prior sets stays COLD`() {
      val firstSession = listOf(EffortSet(day(0), 100.0, 8), EffortSet(day(0).plusSeconds(60), 100.0, 8))
      val secondSession = listOf(EffortSet(day(7), 105.0, 8))
      val series = EffortModel.scoreWithBootstrap(firstSession + secondSession)

      assertThat(series.sets.last().expectationSource).isNull()
      assertThat(series.sets.last().zone).isEqualTo(EffortZone.COLD)
    }

    @Test
    fun `a session's own sets never move its own bootstrap value`() {
      val firstSession = (0 until 3).map { EffortSet(day(0).plusSeconds(it * 60L), 100.0, 8) }
      val fewSets = listOf(EffortSet(day(7), 105.0, 8))
      val manySets = (0 until 8).map { EffortSet(day(7).plusSeconds(it * 60L), 105.0 + it * 50, 8) }

      val withFew = EffortModel.scoreWithBootstrap(firstSession + fewSets)
      val withMany = EffortModel.scoreWithBootstrap(firstSession + manySets)

      val firstDotFew = withFew.sets.first { it.sessionIndex == 1 }
      val firstDotMany = withMany.sets.first { it.sessionIndex == 1 }
      assertThat(firstDotMany.expectation).isWithin(1e-9).of(firstDotFew.expectation!!)
    }

    @Test
    fun `once real session count is reached, output matches score exactly`() {
      val sets = (0..3).map { EffortSet(day(it * 7L), 100.0 + it * 2, 8) } +
        EffortSet(day(28), 108.0, 8)
      val real = EffortModel.score(sets)
      val bootstrapped = EffortModel.scoreWithBootstrap(sets)

      val realFourth = real.sets.filter { it.sessionIndex == 4 }
      val bootstrappedFourth = bootstrapped.sets.filter { it.sessionIndex == 4 }
      assertThat(bootstrappedFourth.map { it.expectation }).isEqualTo(realFourth.map { it.expectation })
      assertThat(bootstrappedFourth.map { it.z }).isEqualTo(realFourth.map { it.z })
      bootstrappedFourth.forEach { assertThat(it.expectationSource).isEqualTo(ExpectationSource.SESSION) }
    }

    @Test
    fun `empty input returns the shared Empty instance`() {
      assertThat(EffortModel.scoreWithBootstrap(emptyList())).isSameInstanceAs(EffortSeries.Empty)
    }
  }

  @Nested
  @DisplayName("degenerate input")
  inner class Degenerate {
    @Test
    fun `a single set has no prediction`() {
      val series = EffortModel.score(listOf(EffortSet(day(0), 100.0, 8)))
      assertThat(series.sets).hasSize(1)
      assertThat(series.sets.single().expectation).isNull()
      assertThat(series.trend).isEmpty()
    }

    @Test
    fun `all sets on one day form a single cold session`() {
      val sets = listOf(
        EffortSet(day(0), 100.0, 8),
        EffortSet(day(0).plusSeconds(60), 105.0, 6)
      )
      val series = EffortModel.score(sets)
      assertThat(series.sets.map { it.sessionIndex }.distinct()).containsExactly(0)
      assertThat(series.trend).isEmpty()
    }

    @Test
    fun `identical sets across many sessions is a degenerate but valid flat history`() {
      val sets = (0 until 6).map { EffortSet(day(it * 7L), 100.0, 8) }
      val series = EffortModel.score(sets)
      assertThat(series.sets).hasSize(6)
      assertThat(series.trend.map { it.expectedCapacity }.all { it.isFinite() }).isTrue()
    }

    @Test
    fun `empty input returns the shared Empty instance`() {
      assertThat(EffortModel.score(emptyList())).isSameInstanceAs(EffortSeries.Empty)
    }
  }
}
