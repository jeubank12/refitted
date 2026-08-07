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

    @Test
    fun `a baseline makes the first plate an increment, not a multiple`() {
      val baseline = EffortConfig.Default.bodyweightBaselineLoad
      val unweighted = EffortModel.capacity(0.0, 8, baselineLoad = baseline)
      val firstPlate = EffortModel.capacity(5.0, 8, baselineLoad = baseline)

      // Against the bare floor this step is 5x - hanging one dumbbell on a set of pull-ups
      // reads as quintupling the load, which is why it used to score IMPLAUSIBLE.
      assertThat(EffortModel.capacity(5.0, 8) / EffortModel.capacity(0.0, 8))
        .isWithin(1e-9).of(5.0)
      assertThat(firstPlate / unweighted).isLessThan(1.1)
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
  @DisplayName("worked examples (spec table, curve at ĉ=133, s=10)")
  inner class WorkedExamples {
    private fun zoneFor(weight: Double, reps: Int): EffortZone =
      EffortModel.zoneOf((EffortModel.capacity(weight, reps) - 133.0) / 10.0)

    @Test
    fun `80 lb x 15 - somewhat below`() {
      assertThat(zoneFor(80.0, 15)).isEqualTo(EffortZone.BELOW)
    }

    @Test
    fun `90 lb x 15 - slightly above, still on the curve`() {
      assertThat(zoneFor(90.0, 15)).isEqualTo(EffortZone.ON_CURVE)
    }

    @Test
    fun `130 lb x 3 - above`() {
      assertThat(zoneFor(130.0, 3)).isEqualTo(EffortZone.GROWTH)
    }

    @Test
    fun `100 lb x 2 - well below`() {
      assertThat(zoneFor(100.0, 2)).isEqualTo(EffortZone.BELOW)
    }

    @Test
    fun `100 lb x 10 - on the curve`() {
      assertThat(zoneFor(100.0, 10)).isEqualTo(EffortZone.ON_CURVE)
    }

    @Test
    fun `100 lb x 12 - above`() {
      assertThat(zoneFor(100.0, 12)).isEqualTo(EffortZone.GROWTH)
    }

    @Test
    fun `180 lb x 5 - far above`() {
      assertThat(zoneFor(180.0, 5)).isEqualTo(EffortZone.IMPLAUSIBLE)
    }

    @Test
    fun `ordering matches the spec table`() {
      // The table is a ladder, so read it back as one: the same inputs in the same order have
      // to give z values that only ever climb.
      val ladder = listOf(
        100.0 to 2,
        80.0 to 15,
        100.0 to 10,
        90.0 to 15,
        100.0 to 12,
        130.0 to 3,
        180.0 to 5
      ).map { (w, r) -> (EffortModel.capacity(w, r) - 133.0) / 10.0 }

      assertThat(ladder).isInOrder()
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
        assertThat(it.zone).isEqualTo(EffortZone.COLD)
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
      assertThat(onCurveScored.zone).isEqualTo(EffortZone.ON_CURVE)
      assertThat(probeScored.z!!).isWithin(1e-6).of(1.0)
      assertThat(probeScored.zone).isEqualTo(EffortZone.GROWTH)
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
    fun `a set's distance from its own expected weight, in bands, is exactly its z`() {
      // The identity a cone drawn at the zone thresholds rests on: if this does not hold
      // exactly, a dot can sit below the line while scoring above it, which is what the
      // session-wide typical-reps conversion used to do to every set with off-average reps.
      val sets = (0 until 10).flatMap { i ->
        listOf(
          EffortSet(day(i * 3L), 100.0 + i * 2, 12),
          EffortSet(day(i * 3L).plusSeconds(180), 110.0 + i * 2, 8),
          EffortSet(day(i * 3L).plusSeconds(420), 115.0 + i * 2, 5)
        )
      }
      val scored = EffortModel.score(sets).sets
        .filter { it.z != null && abs(it.z!!) < EffortConfig.Default.maxAbsZ }

      assertThat(scored).isNotEmpty()
      scored.forEach {
        val band = it.residualScale!! / it.weightScale
        assertThat((it.source.weight - it.expectedWeight!!) / band).isWithin(1e-9).of(it.z!!)
      }
    }

    /** Unloadable work: reps climbing at zero added weight, then a first plate. */
    private fun bodyweightClimb(sessions: Int = 14) = (0 until sessions).flatMap { i ->
      (0 until 3).map { EffortSet(day(i * 3L).plusSeconds(it * 180L), 0.0, 8 + i) }
    }

    @Test
    fun `an unloadable movement expects added weight, rising through zero`() {
      // What progression on bodyweight work looks like: you sit at the bottom of the band
      // adding reps, while the weight the model expects you to be able to hang on climbs from
      // below the axis toward the first increment you could actually load.
      val expected = EffortModel.score(bodyweightClimb()).sets
        .filter { it.setIndexInSession == 0 && it.expectedWeight != null }
        .map { it.expectedWeight!! }

      assertThat(expected).isNotEmpty()
      assertThat(expected.first()).isLessThan(0.0)
      assertThat(expected.last()).isGreaterThan(0.0)
      // Monotone, so "the line is coming up to meet the next plate" is a readable claim.
      expected.zipWithNext { a, b -> assertThat(b).isAtLeast(a) }
    }

    @Test
    fun `adding a first plate to bodyweight work is not implausible`() {
      // The exact transition the chart exists to encourage. Against the bare 1 lb floor this
      // quadrupled demonstrated capacity and clamped z at the maximum.
      val stepUp = bodyweightClimb(sessions = 6) +
        (0 until 3).map { EffortSet(day(18).plusSeconds(it * 180L), 5.0, 13) }

      EffortModel.score(stepUp).sets.takeLast(3).forEach {
        assertThat(it.zone).isNotEqualTo(EffortZone.IMPLAUSIBLE)
      }
    }

    @Test
    fun `the distance-in-bands identity survives zero-weight sets`() {
      val scored = EffortModel.score(bodyweightClimb()).sets
        .filter { it.z != null && abs(it.z!!) < EffortConfig.Default.maxAbsZ }

      assertThat(scored).isNotEmpty()
      scored.forEach {
        val band = it.residualScale!! / it.weightScale
        assertThat((it.source.weight - it.expectedWeight!!) / band).isWithin(1e-9).of(it.z!!)
      }
    }

    @Test
    fun `a loadable exercise carries no baseline`() {
      // The baseline keys off ever having logged an unweighted set, so ordinary barbell work
      // has to be bit-for-bit unaffected by any of this.
      val loaded = (0 until 10).flatMap { i ->
        (0 until 3).map { EffortSet(day(i * 3L).plusSeconds(it * 180L), 100.0 + i * 2, 10) }
      }
      val last = EffortModel.score(loaded).sets.last()

      assertThat(last.weightScale)
        .isWithin(1e-9).of(last.capacity / last.source.weight)
    }

    @Test
    fun `projecting to the reps a set actually did returns its own expected weight`() {
      val sets = (0 until 10).flatMap { i ->
        listOf(
          EffortSet(day(i * 3L), 100.0 + i, 12),
          EffortSet(day(i * 3L).plusSeconds(150), 110.0 + i, 6)
        )
      }

      EffortModel.score(sets).sets.filter { it.expectedWeight != null }.forEach {
        assertThat(it.expectedWeightAt(it.source.reps)!!).isWithin(1e-9).of(it.expectedWeight!!)
      }
    }

    @Test
    fun `asking for more reps asks for less weight`() {
      val sets = (0 until 10).map { EffortSet(day(it * 3L), 100.0 + it, 8) }
      val last = EffortModel.score(sets).sets.last()

      val heavy = last.expectedWeightAt(6)!!
      val target = last.expectedWeightAt(10)!!
      val light = last.expectedWeightAt(15)!!

      assertThat(target).isLessThan(heavy)
      assertThat(light).isLessThan(target)
      // The band travels with it, so a rep count that lowers the bar narrows the miss too.
      assertThat(last.bandHalfWidthAt(15)!!).isLessThan(last.bandHalfWidthAt(6)!!)
    }

    @Test
    fun `a prescribed rep count never changes what a completed set scored`() {
      // The projection is weight-space only. Exceeding the target is progress, not a miss, so
      // nothing about the target may reach the capacity a set is judged on.
      val sets = (0 until 10).map { EffortSet(day(it * 3L), 100.0 + it, 8) }
      val last = EffortModel.score(sets).sets.last()
      val before = last.z

      last.expectedWeightAt(20)
      assertThat(last.z).isEqualTo(before)
      assertThat(last.expectation).isEqualTo(EffortModel.score(sets).sets.last().expectation)
    }

    @Test
    fun `projection stays in added-weight terms on unloadable work`() {
      val last = EffortModel.score(bodyweightClimb()).sets.last()

      // Fewer reps than the lifter has been doing needs weight adding; far more does not.
      assertThat(last.expectedWeightAt(5)!!).isGreaterThan(last.expectedWeightAt(25)!!)
    }

    @Test
    fun `sets in one session expect different weights when their reps differ`() {
      val sets = (0 until 8).flatMap { i ->
        listOf(
          EffortSet(day(i * 3L), 100.0, 12),
          EffortSet(day(i * 3L).plusSeconds(180), 100.0, 6)
        )
      }
      val last = EffortModel.score(sets).sets.takeLast(2)

      // Same weight, half the reps - the bar for the low-rep set has to be higher.
      assertThat(last[1].expectedWeight!!).isGreaterThan(last[0].expectedWeight!!)
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

      // Stated as the ratio it actually constrains - on a flat history the quarter-long layoff
      // lands exactly on the floor, and comparing the two products asserts equality of floats
      // that agree only to within a few ULP.
      assertThat(quarter / week).isAtLeast(EffortConfig.Default.detrainFloor - 1e-9)
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

    /** A block of [count] identical sessions spaced [cadenceDays] apart - a flat plateau. */
    private fun plateau(cadenceDays: Long, count: Int = 12) = (0 until count).flatMap { s ->
      (0 until 3).map { EffortSet(day(s * cadenceDays).plusSeconds(it * 180L), 100.0, 10) }
    }

    @Test
    fun `an exercise trained on a rotation is not treated as detrained`() {
      // The point of the whole chart: zero progress must not read as growth. A fixed calendar
      // grace made anything slower than 10 days permanently detrained, so the lowered bar
      // turned a flat plateau into GROWTH on every set.
      listOf(7L, 14L, 21L, 28L).forEach { cadence ->
        val scored = EffortModel.score(plateau(cadence)).sets.filter { it.z != null }

        assertThat(scored).isNotEmpty()
        scored.forEach {
          assertThat(it.zone).isEqualTo(EffortZone.ON_CURVE)
          assertThat(it.z!!).isWithin(1e-9).of(0.0)
        }
      }
    }

    @Test
    fun `identical progress scores the same however often the exercise comes round`() {
      fun lastZ(cadenceDays: Long): Double {
        val sets = (0 until 12).flatMap { s ->
          (0 until 3).map {
            EffortSet(day(s * cadenceDays).plusSeconds(it * 180L), 100.0 + s * 2.5, 10)
          }
        }
        return EffortModel.score(sets).sets.last().z!!
      }

      // Same gain per exposure either way - only the calendar spacing differs, and spacing is
      // exactly what must not be scored.
      val weekly = lastZ(7)
      assertThat(lastZ(14)).isWithin(0.25).of(weekly)
      assertThat(lastZ(28)).isWithin(0.25).of(weekly)
    }

    @Test
    fun `a gap abnormal for the exercise still detrains, whatever the cadence`() {
      fun comebackZ(cadenceDays: Long): Double {
        val block = plateau(cadenceDays, count = 10)
        val back = (0 until 2).map {
          EffortSet(day(cadenceDays * 9 + 730).plusSeconds(it * 180L), 75.0, 10)
        }
        return EffortModel.score(block + back).sets.last().z!!
      }

      // Two years off is two years off whether the exercise came round every 3 days or every 28.
      val fast = comebackZ(3)
      assertThat(comebackZ(14)).isWithin(1e-6).of(fast)
      assertThat(comebackZ(28)).isWithin(1e-6).of(fast)
    }

    @Test
    fun `one long break does not redefine what a normal gap is`() {
      // The layoff must not feed the cadence average it is measured against, or a single
      // two-year break makes "normal for this exercise" years wide and every later gap free.
      val block = plateau(cadenceDays = 4, count = 10)
      val back = (0 until 3).map { EffortSet(day(36 + 730).plusSeconds(it * 180L), 75.0, 10) }
      val laterGap = (0 until 3).map {
        EffortSet(day(36 + 730 + 200).plusSeconds(it * 180L), 75.0, 10)
      }

      val series = EffortModel.score(block + back + laterGap)
      val comeback = series.sets.first { it.sessionIndex == 10 }
      val afterSecondBreak = series.sets.first { it.sessionIndex == 11 }

      // A 200-day gap on an exercise trained every 4 days is still a layoff the second time.
      assertThat(comeback.expectation!!).isLessThan(comeback.capacity)
      assertThat(afterSecondBreak.expectation!!)
        .isLessThan(afterSecondBreak.capacity)
    }

    @Test
    fun `the cadence is measured once per day, not once per set`() {
      // At set grain every set of a day shares that day's layoff; billing each of them would
      // report a five-set session as five gaps and inflate the cadence the grace comes from.
      val block = (0 until 8).flatMap { s ->
        (0 until 5).map { EffortSet(day(s * 14L).plusSeconds(it * 180L), 100.0, 10) }
      }
      val lean = (0 until 8).map { s -> EffortSet(day(s * 14L), 100.0, 10) }

      val many = EffortModel.scoreWithBootstrap(block).sets.last { it.sessionIndex == 7 }
      val few = EffortModel.scoreWithBootstrap(lean).sets.last { it.sessionIndex == 7 }

      assertThat(many.expectation!!).isWithin(1e-9).of(few.expectation!!)
    }

    @Test
    fun `a comeback is not implausible for landing near the detrained bar`() {
      // Two years off, back at a fraction of the old numbers - the single most ordinary thing a
      // long-dormant exercise can do, and it used to pin z at the clamp and render amber.
      val lastSession = comeback(layoffDays = 730, comebackWeight = 75.0)
        .sets.takeLast(3)

      lastSession.forEach {
        assertThat(it.zone).isNotEqualTo(EffortZone.IMPLAUSIBLE)
        assertThat(abs(it.z!!)).isLessThan(EffortConfig.Default.maxAbsZ)
      }
    }

    @Test
    fun `time away widens the band rather than narrowing it with the bar`() {
      fun bandOf(layoffDays: Long) = comeback(layoffDays).sets.last()
        .let { (it.capacity - it.expectation!!) / it.z!! }

      val week = bandOf(7)
      val quarter = bandOf(90)
      val twoYears = bandOf(730)

      assertThat(quarter).isGreaterThan(week)
      // Past the detrain floor the haircut stops growing, so neither does the uncertainty.
      assertThat(twoYears).isWithin(1e-9).of(quarter)
    }

    @Test
    fun `the widened band is confined to sessions that follow a layoff`() {
      // The session after the comeback has no gap in front of it, so it is scored on the
      // ordinary band again - the widening must not linger once training resumes.
      val block = (0..5).flatMap { s ->
        (0 until 3).map { EffortSet(day(s * 7L).plusSeconds(it * 180L), 100.0, 8) }
      }
      val back = (0 until 3).map { EffortSet(day(400).plusSeconds(it * 180L), 75.0, 8) }
      val next = (0 until 3).map { EffortSet(day(403).plusSeconds(it * 180L), 75.0, 8) }

      val series = EffortModel.score(block + back + next)
      fun bandAt(sessionIndex: Int) = series.sets.last { it.sessionIndex == sessionIndex }
        .let { (it.capacity - it.expectation!!) / it.z!! }

      assertThat(bandAt(7)).isLessThan(bandAt(6))
    }

    /**
     * A block improving by [gainPerSession] every 4 days, then [layoffDays] off, then a rebuild
     * resuming at [comebackWeight] and improving at the same rate.
     */
    private fun rebuild(
      layoffDays: Long = 730,
      comebackWeight: Double = 80.0,
      gainPerSession: Double = 2.0,
      rebuildSessions: Int = 10
    ): EffortSeries {
      val pre = (0 until 12).flatMap { s ->
        (0 until 3).map {
          EffortSet(day(s * 4L).plusSeconds(it * 180L), 100.0 + gainPerSession * s, 10)
        }
      }
      val post = (0 until rebuildSessions).flatMap { s ->
        (0 until 3).map {
          EffortSet(
            day(44 + layoffDays + s * 4L).plusSeconds(it * 180L),
            comebackWeight + gainPerSession * s,
            10
          )
        }
      }
      return EffortModel.score(pre + post)
    }

    /** Fitted capacity-per-day between consecutive rebuild sessions' expectations. */
    private fun rebuildSlopes(series: EffortSeries): List<Double> =
      series.trend.filter { it.sessionIndex > 12 }
        .zipWithNext { a, b ->
          (b.expectedCapacity - a.expectedCapacity) / (b.dayOffset - a.dayOffset)
        }

    @Test
    fun `a rebuild is predicted as growth, not as decline`() {
      // The gap used to sit in the regression's x-domain as a lever arm, tipping the fitted
      // slope negative while the lifter gained every single session.
      val slopes = rebuildSlopes(rebuild())

      assertThat(slopes).isNotEmpty()
      slopes.forEach { assertThat(it).isGreaterThan(0.0) }
    }

    @Test
    fun `the pace carried across a gap tracks the pace before it`() {
      // +2 lb per session on a 4-day cadence, in capacity terms per day.
      val trueSlope = (EffortModel.capacity(102.0, 10) - EffortModel.capacity(100.0, 10)) / 4.0
      val slopes = rebuildSlopes(rebuild())

      // Same order of magnitude and same sign - not a refit from scratch, which lands near zero.
      assertThat(slopes.last()).isGreaterThan(trueSlope / 3.0)
      assertThat(slopes.last()).isLessThan(trueSlope * 2.0)
    }

    @Test
    fun `a steady rebuild stops reading as a surprise every session`() {
      val rebuilt = rebuild()
      val postLayoff = rebuilt.sets.filter { it.sessionIndex > 12 && it.z != null }

      assertThat(postLayoff).isNotEmpty()
      // Predicted, not marvelled at: nothing drifts toward the outlier line.
      postLayoff.forEach { assertThat(it.zone).isNotEqualTo(EffortZone.IMPLAUSIBLE) }
      assertThat(postLayoff.map { it.z!! }.max()).isLessThan(1.5)
    }

    @Test
    fun `a session of nothing but a light feeler cannot re-level the history`() {
      val block = (0..7).flatMap { s ->
        (0 until 3).map { EffortSet(day(s * 4L).plusSeconds(it * 180L), 200.0, 8) }
      }
      val feeler = listOf(EffortSet(day(28 + 730), 20.0, 5))
      val next = (0 until 3).map {
        EffortSet(day(28 + 730 + 4).plusSeconds(it * 180L), 150.0, 8)
      }

      val settled = EffortModel.score(block + feeler + next).sets.last()

      // The clamp bounds the *rescale* of retained history, not the feeler's own fold-in - a
      // 20 lb set logged as a whole session is still evidence and still moves the fit. What
      // must not happen is the model concluding this is a 20 lb lifter.
      assertThat(settled.expectation!!).isGreaterThan(EffortModel.capacity(20.0, 5) * 2.0)
    }

    @Test
    fun `an ordinary gap inside the grace is left completely alone`() {
      // The whole comeback path must be inert during normal training - no slide, no re-level.
      val steady = (0..11).flatMap { s ->
        (0 until 3).map { EffortSet(day(s * 4L).plusSeconds(it * 180L), 100.0 + s, 10) }
      }
      val series = EffortModel.score(steady)

      series.trend.forEach { assertThat(it.expectedCapacity).isGreaterThan(0.0) }
      // A pure straight line in, a pure straight line out.
      val slopes = series.trend.zipWithNext { a, b ->
        (b.expectedCapacity - a.expectedCapacity) / (b.dayOffset - a.dayOffset)
      }
      slopes.forEach { assertThat(it).isGreaterThan(0.0) }
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

    /** Six weekly sessions, a year off, then a comeback whose sets are [comebackSets]. */
    private fun comebackSession(vararg comebackSets: Pair<Double, Int>): List<EffortSet> {
      val block = (0..5).flatMap { s ->
        (0 until 3).map { EffortSet(day(s * 7L).plusSeconds(it * 180L), 100.0, 8) }
      }
      return block + comebackSets.mapIndexed { i, (w, r) ->
        EffortSet(day(400).plusSeconds(i * 180L), w, r)
      }
    }

    @Test
    fun `a cautious first set back reads light once the working sets land`() {
      // Exactly the live-strip story: open at a weight you are unsure of, and it looks fine
      // against a guess the model admits it cannot make; find your real level on the next set
      // and the opener is revealed as the feeler it was.
      val alone = EffortModel.scoreWithBootstrap(comebackSession(75.0 to 8))
      val settled = EffortModel.scoreWithBootstrap(
        comebackSession(75.0 to 8, 95.0 to 8, 95.0 to 8)
      )

      val openerAlone = alone.sets.last { it.sessionIndex == 6 }
      val openerSettled = settled.sets.first { it.sessionIndex == 6 }

      assertThat(openerSettled.z!!).isLessThan(openerAlone.z!!)
      assertThat(openerSettled.zone).isEqualTo(EffortZone.BELOW)
    }

    @Test
    fun `the band closes as the guess becomes a measurement`() {
      val oneSet = EffortModel.scoreWithBootstrap(comebackSession(95.0 to 8))
        .sets.last { it.sessionIndex == 6 }
      val threeSets = EffortModel.scoreWithBootstrap(
        comebackSession(95.0 to 8, 95.0 to 8, 95.0 to 8)
      ).sets.last { it.sessionIndex == 6 }

      assertThat(threeSets.residualScale!!).isLessThan(oneSet.residualScale!!)
    }

    @Test
    fun `the long-term history never recalibrates within a session`() {
      // score() is what the drawer renders, and a dot that changes colour while you scroll is
      // a different promise from a live strip that is telling you what to do next.
      val sets = comebackSession(75.0 to 8, 95.0 to 8, 95.0 to 8)
      val session = EffortModel.score(sets).sets.filter { it.sessionIndex == 6 }

      assertThat(session).hasSize(3)
      session.zipWithNext { a, b ->
        assertThat(b.expectation!!).isWithin(1e-9).of(a.expectation!!)
      }
      // And that shared expectation is the time-based guess, untouched by the session itself.
      assertThat(session.first().expectation!!)
        .isLessThan(EffortModel.capacity(100.0, 8))
    }

    @Test
    fun `recalibration does not leak into the fit's own error estimate`() {
      // The residual accumulator measures prediction error. Feeding it a level this session
      // helped set would report the fit as far more accurate than it is, and collapse the band
      // for every session after it.
      val sets = comebackSession(95.0 to 8, 95.0 to 8, 95.0 to 8) +
        (0 until 3).map { EffortSet(day(407).plusSeconds(it * 180L), 95.0, 8) }

      val viaStrip = EffortModel.scoreWithBootstrap(sets).sets.last().residualScale!!
      val viaDrawer = EffortModel.score(sets).sets.last().residualScale!!

      assertThat(viaStrip).isWithin(1e-9).of(viaDrawer)
    }

    @Test
    fun `a layoff is charged once, not once per set`() {
      // The exploded fit sees one fit session per set, so a five-set comeback must not age the
      // history by five layoffs. Only two prior days, so the session fit is still cold here and
      // the values under test are genuinely the set-grain ones.
      val block = (0..1).flatMap { s ->
        (0 until 3).map { EffortSet(day(s * 7L).plusSeconds(it * 180L), 100.0, 8) }
      }
      val comeback = (0 until 5).map { EffortSet(day(200).plusSeconds(it * 180L), 100.0, 8) }

      val scored = EffortModel.scoreWithBootstrap(block + comeback)
        .sets.filter { it.sessionIndex == 2 && it.expectation != null }

      assertThat(scored).hasSize(5)
      // Billing the gap per set decays the history by 0.5^(200/90) again on every one of them,
      // so by the fifth set the expectation would have collapsed to a fraction of a percent.
      val first = scored.first().expectation!!
      scored.forEach { assertThat(it.expectation!!).isGreaterThan(first * 0.8) }
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
      val sets = (0..5).flatMap { session ->
        (0 until 3).map { EffortSet(day(session * 7L).plusSeconds(it * 120L), 100.0 + session * 2, 8) }
      }
      val series = EffortModel.scoreWithBootstrap(sets)

      val lastBootstrap = series.sets.last { it.expectationSource == ExpectationSource.BOOTSTRAP }
      val firstReal = series.sets.first { it.expectationSource == ExpectationSource.SESSION }

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
}
