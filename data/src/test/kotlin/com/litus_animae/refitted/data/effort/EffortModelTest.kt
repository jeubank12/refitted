package com.litus_animae.refitted.data.effort

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

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
    fun `reps past the cap keep counting, with diminishing returns`() {
      val atCap = EffortModel.capacity(100.0, 15)
      val past = EffortModel.capacity(100.0, 20)
      val further = EffortModel.capacity(100.0, 25)

      assertThat(past).isGreaterThan(atCap)
      assertThat(further).isGreaterThan(past)
      assertThat(further - past).isLessThan(past - atCap)
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
  @DisplayName("weightForReps")
  inner class WeightForReps {
    @Test
    fun `round-trips capacity back to the original weight at the same rep count`() {
      listOf(80.0 to 15, 90.0 to 15, 130.0 to 3, 100.0 to 10, 180.0 to 5).forEach { (weight, reps) ->
        val capacity = EffortModel.capacity(weight, reps)
        assertThat(EffortModel.weightForReps(capacity, reps)).isWithin(1e-6).of(weight)
      }
    }

    @Test
    fun `is heavier at fewer reps for the same capacity`() {
      val capacity = EffortModel.capacity(100.0, 10)
      assertThat(EffortModel.weightForReps(capacity, 4))
        .isGreaterThan(EffortModel.weightForReps(capacity, 15))
    }
  }

  @Nested
  @DisplayName("effectiveReps")
  inner class RepSoftCap {
    @Test
    fun `is the identity at and below the cap`() {
      (0..EffortConfig.Default.repCap).forEach {
        assertThat(EffortModel.effectiveReps(it)).isWithin(1e-9).of(it.toDouble())
      }
    }

    @Test
    fun `is continuous and smooth across the cap`() {
      val cap = EffortConfig.Default.repCap
      assertThat(EffortModel.effectiveReps(cap)).isWithin(1e-9).of(cap.toDouble())
      // Matching first derivative: the first rep past the cap is still worth ~a whole rep.
      assertThat(EffortModel.effectiveReps(cap + 1) - EffortModel.effectiveReps(cap))
        .isWithin(0.06).of(1.0)
    }

    @Test
    fun `is strictly increasing with strictly shrinking gains past the cap`() {
      val steps = (15..60).map { EffortModel.effectiveReps(it) }
      steps.zipWithNext().forEach { (a, b) -> assertThat(b).isGreaterThan(a) }
      steps.zipWithNext { a, b -> b - a }.zipWithNext().forEach { (first, second) ->
        assertThat(second).isLessThan(first)
      }
    }

    @Test
    fun `a zero scale restores the old hard clamp`() {
      val hardCapped = EffortConfig.Default.copy(repSoftCapScale = 0.0)
      assertThat(EffortModel.effectiveReps(40, hardCapped))
        .isWithin(1e-9).of(EffortConfig.Default.repCap.toDouble())
    }

    @Test
    fun `the fit's typical reps are no longer pinned at the cap`() {
      val cap = EffortConfig.Default.repCap.toDouble()
      val highRep = (0..5).map { EffortSet(day(it * 7L), 0.0, 30) }

      val trend = EffortModel.score(highRep).trend
      assertThat(trend).isNotEmpty()
      trend.forEach { assertThat(it.typicalReps).isGreaterThan(cap) }
    }

    @Test
    fun `bodyweight progress keeps registering past the cap`() {
      val plateaued = (0..5).map { EffortSet(day(it * 7L), 0.0, 20) }
      val improving = (0..5).map { EffortSet(day(it * 7L), 0.0, 20 + it * 3) }

      val plateauedLast = EffortModel.score(plateaued).sets.last()
      val improvingLast = EffortModel.score(improving).sets.last()
      assertThat(improvingLast.capacity).isGreaterThan(plateauedLast.capacity)
      assertThat(improvingLast.z!!).isGreaterThan(plateauedLast.z!!)
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
  @DisplayName("repSize")
  inner class RepSize {
    @Test
    fun `scales linearly up to repCap`() {
      val cap = EffortConfig.Default.repCap
      assertThat(EffortModel.repSize(0)).isEqualTo(0f)
      assertThat(EffortModel.repSize(cap / 2)).isEqualTo((cap / 2).toFloat() / cap)
      assertThat(EffortModel.repSize(cap)).isEqualTo(1f)
    }

    @Test
    fun `clamps at 1 past repCap`() {
      val cap = EffortConfig.Default.repCap
      assertThat(EffortModel.repSize(cap * 3)).isEqualTo(1f)
    }

    @Test
    fun `never goes negative for zero or negative reps`() {
      assertThat(EffortModel.repSize(0)).isEqualTo(0f)
      assertThat(EffortModel.repSize(-1)).isEqualTo(0f)
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
  @DisplayName("rest-gap density credit")
  inner class Density {
    private fun session(at: Instant, restSeconds: Long, count: Int = 3) =
      (0 until count).map { EffortSet(at.plusSeconds(it * restSeconds), 100.0, 8) }

    private fun capacitiesAfterFirst(sets: List<EffortSet>) =
      EffortModel.score(sets).sets.drop(1).map { it.capacity }

    @Test
    fun `sets taken close together demonstrate more than the same sets taken far apart`() {
      val dense = capacitiesAfterFirst(session(day(0), restSeconds = 30))
      val sparse = capacitiesAfterFirst(session(day(0), restSeconds = 300))

      dense.zip(sparse).forEach { (d, s) -> assertThat(d).isGreaterThan(s) }
    }

    @Test
    fun `credit tapers off as rest grows and is gone by the reference rest`() {
      val short = capacitiesAfterFirst(session(day(0), restSeconds = 60))
      val medium = capacitiesAfterFirst(session(day(0), restSeconds = 120))
      val reference = capacitiesAfterFirst(
        session(day(0), restSeconds = EffortConfig.Default.restReferenceSeconds.toLong())
      )
      val bare = EffortModel.capacity(100.0, 8)

      assertThat(short.first()).isGreaterThan(medium.first())
      assertThat(medium.first()).isGreaterThan(reference.first())
      assertThat(reference.first()).isWithin(1e-9).of(bare)
    }

    @Test
    fun `a long break is neutral, never a penalty`() {
      val bare = EffortModel.capacity(100.0, 8)

      assertThat(capacitiesAfterFirst(session(day(0), restSeconds = 1800)).first())
        .isWithin(1e-9).of(bare)
      assertThat(capacitiesAfterFirst(session(day(0), restSeconds = 7200)).first())
        .isWithin(1e-9).of(bare)
    }

    @Test
    fun `implausibly short gaps claim no credit`() {
      // The strip stamps every not-yet-persisted record with the same Instant.now(), so
      // near-zero gaps mean "logged in a burst", not "rested for no time at all".
      val bare = EffortModel.capacity(100.0, 8)

      assertThat(capacitiesAfterFirst(session(day(0), restSeconds = 0)).first())
        .isWithin(1e-9).of(bare)
      assertThat(capacitiesAfterFirst(session(day(0), restSeconds = 5)).first())
        .isWithin(1e-9).of(bare)
    }

    @Test
    fun `the credit is capped at the configured bonus`() {
      val bare = EffortModel.capacity(100.0, 8)
      val backToBack = capacitiesAfterFirst(session(day(0), restSeconds = 12)).first()

      assertThat(backToBack / bare)
        .isWithin(1e-9).of(1.0 + EffortConfig.Default.densityBonusMax)
    }

    @Test
    fun `the first set of a session is never credited - nothing preceded it`() {
      val sets = session(day(0), restSeconds = 15)

      assertThat(EffortModel.score(sets).sets.first().capacity)
        .isWithin(1e-9).of(EffortModel.capacity(100.0, 8))
    }

    @Test
    fun `bodyweight work can show progress on rest alone`() {
      // Same weight (none) and same reps every session - the only thing that improves is how
      // tightly the sets are packed, which for unloadable work is the whole story.
      val loose = (0..5).flatMap { s ->
        (0 until 3).map { EffortSet(day(s * 7L).plusSeconds(it * 240L), 0.0, 12) }
      }
      val tightening = (0..5).flatMap { s ->
        val rest = (240L - s * 40L).coerceAtLeast(30L)
        (0 until 3).map { EffortSet(day(s * 7L).plusSeconds(it * rest), 0.0, 12) }
      }

      val looseLast = EffortModel.score(loose).sets.last()
      val tighteningLast = EffortModel.score(tightening).sets.last()

      assertThat(tighteningLast.capacity).isGreaterThan(looseLast.capacity)
      assertThat(tighteningLast.z!!).isGreaterThan(looseLast.z!!)
    }
  }

  @Nested
  @DisplayName("cooldown after time away")
  inner class Cooldown {
    /** A steady weekly block, then a gap of [layoffDays], then one more session. */
    private fun comeback(layoffDays: Long, comebackWeight: Double = 100.0): EffortSeries {
      val block = (0..5).flatMap { s ->
        (0 until 3).map { EffortSet(day(s * 7L).plusSeconds(it * 180L), 100.0, 8) }
      }
      val after = (0 until 3).map {
        EffortSet(day(35 + layoffDays).plusSeconds(it * 180L), comebackWeight, 8)
      }
      return EffortModel.score(block + after)
    }

    private fun comebackExpectation(layoffDays: Long) = comeback(layoffDays).sets.last().expectation!!

    @Test
    fun `a normal training cadence is left alone`() {
      // Inside the grace period, so nothing about an ordinary week changes.
      assertThat(comebackExpectation(7)).isWithin(1e-9).of(comebackExpectation(3))
    }

    @Test
    fun `the longer the layoff, the lower the bar on return`() {
      val week = comebackExpectation(7)
      val threeWeeks = comebackExpectation(21)
      val sixWeeks = comebackExpectation(40)

      assertThat(threeWeeks).isLessThan(week)
      assertThat(sixWeeks).isLessThan(threeWeeks)
    }

    @Test
    fun `time off costs something, not everything`() {
      val week = comebackExpectation(7)
      val quarter = comebackExpectation(90)

      assertThat(quarter).isAtLeast(week * EffortConfig.Default.detrainFloor)
      // Past the point the floor binds, more time away costs nothing further.
      assertThat(comebackExpectation(365)).isWithin(1e-9).of(quarter)
      assertThat(comebackExpectation(3650)).isWithin(1e-9).of(quarter)
    }

    @Test
    fun `the first session back is not slammed for being off the old pace`() {
      val straightOn = comeback(layoffDays = 7, comebackWeight = 60.0)
      val afterMonths = comeback(layoffDays = 120, comebackWeight = 60.0)

      val straightOnZ = straightOn.sets.last { it.sessionIndex == 6 }.z!!
      val afterMonthsZ = afterMonths.sets.last { it.sessionIndex == 6 }.z!!
      assertThat(afterMonthsZ).isGreaterThan(straightOnZ)
    }

    @Test
    fun `coming back lighter after months off reads as effort, not failure`() {
      // The whole point: the curve used to freeze where it was left, so a comeback at anything
      // under the old numbers read BELOW no matter how hard it was.
      val comeback = comeback(layoffDays = 120, comebackWeight = 75.0)
      val lastSession = comeback.sets.filter { it.sessionIndex == comeback.sets.last().sessionIndex }

      assertThat(lastSession).isNotEmpty()
      lastSession.forEach { assertThat(it.zone).isNotEqualTo(EffortZone.BELOW) }
    }

    @Test
    fun `stale history stops outweighing what happened since`() {
      // Identical session counts and identical numbers either way - only the calendar distance
      // between the old block and the recent one differs. When that distance ages the old block
      // out, the curve settles on what the lifter is doing now; when it doesn't, the abandoned
      // level is still dragging the fit around four sessions later.
      fun settledExpectation(gapDays: Long): Double {
        val old = (0..5).map { EffortSet(day(it * 7L), 200.0, 8) }
        val recent = (0..3).map { EffortSet(day(35 + gapDays + it * 7L), 100.0, 8) }
        return EffortModel.score(old + recent).sets.last().expectation!!
      }

      val recentLevel = EffortModel.capacity(100.0, 8)
      val stale = abs(settledExpectation(gapDays = 500) - recentLevel)
      val contiguous = abs(settledExpectation(gapDays = 7) - recentLevel)

      assertThat(stale).isLessThan(contiguous)
    }

    @Test
    fun `a layoff is charged once, not once per set`() {
      // The exploded fit sees one fit session per set, so a five-set comeback must not age the
      // history by five layoffs.
      val many = (0 until 5).map { EffortSet(day(200).plusSeconds(it * 180L), 100.0, 8) }
      val few = listOf(EffortSet(day(200), 100.0, 8))
      val block = (0..5).flatMap { s ->
        (0 until 3).map { EffortSet(day(s * 7L).plusSeconds(it * 180L), 100.0, 8) }
      }

      val withMany = EffortModel.scoreWithBootstrap(block + many)
      val withFew = EffortModel.scoreWithBootstrap(block + few)

      assertThat(withMany.sets.first { it.sessionIndex == 6 }.expectation!!)
        .isWithin(1e-9).of(withFew.sets.first { it.sessionIndex == 6 }.expectation!!)
    }
  }

  @Nested
  @DisplayName("sustained load")
  inner class SustainedLoad {
    // Rest held constant across every shape so only the shape itself moves the numbers.
    private fun sessionsOf(shape: List<Pair<Double, Int>>, count: Int = 5) =
      (0 until count).flatMap { s ->
        shape.mapIndexed { i, (weight, reps) ->
          EffortSet(day(s * 7L).plusSeconds(i * 180L), weight, reps)
        }
      }

    private fun expectationAfter(shape: List<Pair<Double, Int>>): Double =
      EffortModel.score(sessionsOf(shape)).sets.last().expectation!!

    @Test
    fun `holding the same weight beats fading down from it`() {
      val held = expectationAfter(listOf(100.0 to 8, 100.0 to 8, 100.0 to 8))
      val faded = expectationAfter(listOf(100.0 to 8, 80.0 to 8, 60.0 to 8))

      assertThat(held).isGreaterThan(faded)
    }

    @Test
    fun `holding reps beats fading reps at the same weight`() {
      val held = expectationAfter(listOf(100.0 to 10, 100.0 to 10, 100.0 to 10))
      val faded = expectationAfter(listOf(100.0 to 10, 100.0 to 8, 100.0 to 6))

      assertThat(held).isGreaterThan(faded)
    }

    @Test
    fun `a weight fade is discounted harder than a rep fade`() {
      val repFade = expectationAfter(listOf(100.0 to 10, 100.0 to 8, 100.0 to 6))
      val weightFade = expectationAfter(listOf(100.0 to 10, 80.0 to 10, 60.0 to 10))

      assertThat(weightFade).isLessThan(repFade)
    }

    @Test
    fun `ramping up to a top set is warm-up, not fade`() {
      val ramped = expectationAfter(listOf(60.0 to 8, 80.0 to 8, 100.0 to 8))
      val held = expectationAfter(listOf(100.0 to 8, 100.0 to 8, 100.0 to 8))

      assertThat(ramped).isWithin(1e-9).of(held)
    }

    @Test
    fun `a single-set session is never discounted`() {
      val single = expectationAfter(listOf(100.0 to 8))
      val held = expectationAfter(listOf(100.0 to 8, 100.0 to 8, 100.0 to 8))

      assertThat(single).isWithin(1e-9).of(held)
    }

    @Test
    fun `the discount is bounded by the configured maximum`() {
      val held = expectationAfter(listOf(100.0 to 8, 100.0 to 8, 100.0 to 8))
      val collapsed = expectationAfter(listOf(100.0 to 8, 20.0 to 1, 20.0 to 1))

      assertThat(collapsed).isAtLeast(held * (1 - EffortConfig.Default.sustainPenaltyMax) - 1e-9)
    }

    @Test
    fun `the session value never rises above the session's best set`() {
      // A discount off the best, not a bonus above it - otherwise the trend line drifts above
      // every set that produced it and everything reads BELOW.
      val series = EffortModel.score(sessionsOf(listOf(100.0 to 8, 100.0 to 8, 100.0 to 8)))
      val lastSessionIndex = series.sets.last().sessionIndex
      val bestBefore = series.sets
        .filter { it.sessionIndex < lastSessionIndex }
        .maxOf { it.capacity }

      assertThat(series.sets.last().expectation!!).isAtMost(bestBefore + 1e-9)
    }
  }

  @Nested
  @DisplayName("scoreWithBootstrap")
  inner class Bootstrap {
    @Test
    fun `the first sets ever are COLD - nothing prior exists to compare them to`() {
      val firstSession = (0 until 3).map { EffortSet(day(0).plusSeconds(it * 60L), 100.0, 8) }
      val series = EffortModel.scoreWithBootstrap(firstSession)

      assertThat(series.trend).isEmpty()
      series.sets.forEach {
        assertThat(it.expectationSource).isNull()
        assertThat(it.zone).isEqualTo(EffortZone.COLD)
      }
    }

    @Test
    fun `a first-ever session warms up from its own earlier sets`() {
      val firstSession = (0 until 6).map { EffortSet(day(0).plusSeconds(it * 60L), 100.0, 8) }
      val series = EffortModel.scoreWithBootstrap(firstSession)

      val (cold, warm) = series.sets.partition { it.expectationSource == null }
      assertThat(cold).hasSize(EffortConfig.Default.minPriorSetsForBootstrap)
      assertThat(warm).isNotEmpty()
      warm.forEach {
        assertThat(it.expectationSource).isEqualTo(ExpectationSource.BOOTSTRAP)
        assertThat(it.zone).isNotEqualTo(EffortZone.COLD)
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
    fun `once real session count is reached, a completed session matches score exactly`() {
      // Session 4 is completed (session 5, "today," follows it), so it should match the real
      // fit exactly - unlike the live session (see the "always exploded" tests below).
      val sets = (0..4).map { EffortSet(day(it * 7L), 100.0 + it * 2, 8) } +
        EffortSet(day(35), 110.0, 8)
      val real = EffortModel.score(sets)
      val bootstrapped = EffortModel.scoreWithBootstrap(sets)

      val realFourth = real.sets.filter { it.sessionIndex == 4 }
      val bootstrappedFourth = bootstrapped.sets.filter { it.sessionIndex == 4 }
      assertThat(bootstrappedFourth.map { it.expectation }).isEqualTo(realFourth.map { it.expectation })
      assertThat(bootstrappedFourth.map { it.z }).isEqualTo(realFourth.map { it.z })
      bootstrappedFourth.forEach { assertThat(it.expectationSource).isEqualTo(ExpectationSource.SESSION) }
    }

    @Test
    fun `the live (most recent) session is always exploded, even once real session count is reached`() {
      val sets = (0..4).map { EffortSet(day(it * 7L), 100.0 + it * 2, 8) } +
        (0 until 3).map { EffortSet(day(35).plusSeconds(it * 120L), 110.0, 8) }
      val bootstrapped = EffortModel.scoreWithBootstrap(sets)

      val live = bootstrapped.sets.filter { it.sessionIndex == 5 }
      assertThat(live).isNotEmpty()
      live.forEach { assertThat(it.expectationSource).isEqualTo(ExpectationSource.BOOTSTRAP) }

      // Genuinely per-set: three identical (weight, reps) sets still land on the exploded fit's
      // own strictly-increasing set-sequence x, so their expectation isn't forced flat.
      assertThat(live.map { it.expectation }.distinct().size).isGreaterThan(1)
    }

    @Test
    fun `the live session's trend point comes from the exploded fit too`() {
      val sets = (0..4).map { EffortSet(day(it * 7L), 100.0 + it * 2, 8) } +
        EffortSet(day(35), 110.0, 8)
      val real = EffortModel.score(sets)
      val bootstrapped = EffortModel.scoreWithBootstrap(sets)

      // The real fit does have an opinion on session 5 (5 prior sessions, well past
      // minPriorSessions) - scoreWithBootstrap has to deliberately override it rather than just
      // passing it through, which is exactly what's under test here.
      assertThat(real.trend.single { it.sessionIndex == 5 }.expectedCapacity).isFinite()
      assertThat(bootstrapped.trend.single { it.sessionIndex == 5 }.expectationSource)
        .isEqualTo(ExpectationSource.BOOTSTRAP)
    }

    @Test
    fun `the bootstrap output stays 1 to 1 and in order with score's`() {
      val sets = (0..2).flatMap { session ->
        (0 until 3).map { EffortSet(day(session * 7L).plusSeconds(it * 120L), 100.0, 8) }
      }
      val real = EffortModel.score(sets)
      val bootstrapped = EffortModel.scoreWithBootstrap(sets)

      assertThat(bootstrapped.sets.map { it.source }).isEqualTo(real.sets.map { it.source })
      assertThat(bootstrapped.sets.map { it.sessionIndex }).isEqualTo(real.sets.map { it.sessionIndex })
    }

    @Test
    fun `the trend does not jump at the handoff to the real fit`() {
      // One extra trailing session ("today") past the historical handoff under test, excluded
      // below - it's always exploded regardless of maturity (see the "live session" tests),
      // which is a different rule than the completed-history handoff this test checks.
      val sets = (0..5).flatMap { session ->
        (0 until 3).map { EffortSet(day(session * 7L).plusSeconds(it * 120L), 100.0 + session * 2, 8) }
      } + EffortSet(day(42), 112.0, 8)
      val series = EffortModel.scoreWithBootstrap(sets)
      val completed = series.sets.filter { it.sessionIndex < 6 }

      val lastBootstrap = completed.last { it.expectationSource == ExpectationSource.BOOTSTRAP }
      val firstReal = completed.first { it.expectationSource == ExpectationSource.SESSION }

      // Different grains, so the numbers won't match exactly - but on a steady history the line
      // has to stay continuous where the strip switches from the dashed stand-in to the real
      // fit, rather than stepping to a visibly different level.
      assertThat(firstReal.expectation!!)
        .isWithin(0.1 * lastBootstrap.expectation!!).of(lastBootstrap.expectation)
      assertThat(firstReal.expectedWeight!!)
        .isWithin(0.1 * lastBootstrap.expectedWeight!!).of(lastBootstrap.expectedWeight)
    }

    @Test
    fun `a warm-up set never drags the bootstrap fit down onto the working set behind it`() {
      // A set-granularity fit has no session-best to hide warm-ups behind: folding the 60 lb
      // opener in as if it were a session outcome used to pull the expectation under 100 and
      // make the 102 lb top set that followed it read IMPLAUSIBLE.
      val sets = (0..2).flatMap { session ->
        listOf(
          EffortSet(day(session * 7L), 60.0, 10),
          EffortSet(day(session * 7L).plusSeconds(200), 100.0 + session * 2, 8),
          EffortSet(day(session * 7L).plusSeconds(400), 95.0 + session * 2, 7)
        )
      }
      val series = EffortModel.scoreWithBootstrap(sets)
      val working = series.sets.filter { it.source.weight >= 90.0 && it.expectation != null }

      assertThat(working).isNotEmpty()
      working.forEach { assertThat(it.zone).isNotEqualTo(EffortZone.IMPLAUSIBLE) }
      // Monotone: each warm-up is skipped rather than yanking the line backwards.
      series.sets.mapNotNull { it.expectation }.zipWithNext().forEach { (a, b) ->
        assertThat(b).isAtLeast(a)
      }
    }

    @Test
    fun `every scored set carries the expected weight its expectation implies`() {
      val sets = (0..4).flatMap { session ->
        (0 until 3).map { EffortSet(day(session * 7L).plusSeconds(it * 120L), 100.0 + session, 8) }
      }
      val series = EffortModel.scoreWithBootstrap(sets)

      series.sets.forEach {
        if (it.expectation == null) {
          assertThat(it.expectedWeight).isNull()
        } else {
          assertThat(it.expectedWeight!!).isGreaterThan(0.0)
          assertThat(it.expectedWeight).isLessThan(it.expectation)
        }
      }
    }

    @Test
    fun `how many warm-ups get skipped does not move the next working set`() {
      // Skipped sets still advance the fit's x while lastPriorX stays put, so the gap the
      // extrapolation clamp sees tracks set *volume*. Bounding it with maxExtrapolationDays - a
      // calendar quantity - meant 15 skipped warm-ups tripped a clamp meant for long layoffs.
      // Past the set-denominated bound, skipping more must make no difference at all.
      fun withWarmUps(count: Int): Double {
        // Three rising working sets so the fit has a slope worth extrapolating, then a run of
        // warm-ups that all get skipped, then the working set whose expectation is measured -
        // the only set whose x sits far past the last one the fit actually folded.
        val sets = mutableListOf(
          EffortSet(day(0), 200.0, 8),
          EffortSet(day(0).plusSeconds(200), 205.0, 8),
          EffortSet(day(0).plusSeconds(400), 210.0, 8)
        )
        repeat(count) { sets.add(EffortSet(day(0).plusSeconds(600L + it * 200L), 60.0, 8)) }
        sets.add(EffortSet(day(0).plusSeconds(600L + count * 200L), 210.0, 8))
        return EffortModel.scoreWithBootstrap(sets).sets.last().expectation!!
      }

      assertThat(withWarmUps(25)).isWithin(1e-9).of(withWarmUps(5))
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

  @Nested
  @DisplayName("stagnation-biased low anchor")
  inner class StagnationLowAnchor {
    private val repCap = EffortConfig.Default.repCap.toDouble()

    @Test
    fun `holds at repCap with no repeated weight-and-reps streak`() {
      // A different rep count every session - weight and reps never match the prior session, so
      // the streak never starts.
      val sets = (0 until 6).map { EffortSet(day(it * 7L), 100.0, 8 + it) }
      val series = EffortModel.score(sets)
      assertThat(series.sets.map { it.lowAnchorReps }.all { it == repCap }).isTrue()
    }

    @Test
    fun `slides from repCap toward the repeated rep count as the streak grows`() {
      // Same weight and reps every session - matches the hand-worked example: after five
      // repeats of "10 reps" the anchor is (10*5 + 15) / 6.
      val sets = (0..6).map { EffortSet(day(it * 7L), 95.0, 10) }
      val series = EffortModel.score(sets)
      val byIndex = series.sets.associateBy { it.sessionIndex }

      assertThat(byIndex.getValue(0).lowAnchorReps).isEqualTo(repCap)
      assertThat(byIndex.getValue(1).lowAnchorReps).isEqualTo(repCap)
      assertThat(byIndex.getValue(2).lowAnchorReps).isWithin(1e-9).of((10.0 + 15.0) / 2.0)
      assertThat(byIndex.getValue(6).lowAnchorReps).isWithin(1e-9).of((10.0 * 5 + 15.0) / 6.0)

      // Monotonically sliding toward the repeated value (10), never overshooting past it.
      val sequence = (0..6).map { byIndex.getValue(it).lowAnchorReps }
      sequence.zipWithNext().forEach { (a, b) -> assertThat(b).isAtMost(a) }
      assertThat(sequence.last()).isGreaterThan(10.0)
    }

    @Test
    fun `resets to repCap the session after weight or reps changes`() {
      val stagnant = (0..4).map { EffortSet(day(it * 7L), 95.0, 10) }
      val broken = stagnant + listOf(
        EffortSet(day(35), 95.0, 11), // reps improve - breaks the streak
        EffortSet(day(42), 95.0, 10)  // back to the old reps, but the streak already reset
      )
      val series = EffortModel.score(broken)
      val byIndex = series.sets.associateBy { it.sessionIndex }

      assertThat(byIndex.getValue(4).lowAnchorReps).isLessThan(repCap)
      assertThat(byIndex.getValue(6).lowAnchorReps).isEqualTo(repCap)
    }

    @Test
    fun `the funnel band's low anchor never moves the target (4-rep) edge`() {
      val sets = (0..6).map { EffortSet(day(it * 7L), 95.0, 10) }
      val series = EffortModel.score(sets)
      val stagnated = series.sets.last()
      val expectation = stagnated.expectation!!
      val target = EffortModel.weightForReps(expectation, 4)
      val bottom = EffortModel.weightForReps(expectation, stagnated.lowAnchorReps)

      assertThat(stagnated.lowAnchorReps).isLessThan(repCap)
      assertThat(target).isGreaterThan(bottom)
      assertThat(bottom).isGreaterThan(EffortModel.weightForReps(expectation, repCap))
    }
  }
}
