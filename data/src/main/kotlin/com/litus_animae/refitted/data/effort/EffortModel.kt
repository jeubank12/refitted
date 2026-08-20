package com.litus_animae.refitted.data.effort

import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Scores each completed set's demonstrated capacity (weight x reps, Epley-style) against an
 * adaptive, causally-fit expectation of what a session "should" look like given training
 * history - never a static baseline, and a session's own outcome never judges itself. A set
 * scores larger (see [bubbleSize]) the closer it lands to or just above that expectation,
 * and shrinks again once it's implausibly far above - a likely fluke or typo, still worth
 * showing but not the target to chase.
 */
object EffortModel {

  const val NEUTRAL_SIZE = 0.45f

  // z -> bubble size anchors. Piecewise-linear rather than smoothstep so "slightly above
  // the curve" (z around 0.2-0.5) stays visually distinct from "on the curve" (z = 0).
  private val HUMP_ANCHORS = listOf(
    -2.0 to 0.00,
    -1.0 to 0.30,
    0.0 to 0.60,
    0.5 to 0.85,
    1.0 to 1.00,
    1.5 to 0.90,
    2.5 to 0.40
  )

  /**
   * Epley-style estimated 1RM, the single scale weight×reps sets are compared on.
   *
   * Floored at 1.0, not 0.0 - a bodyweight-only set (no explicit "bodyweight" flag exists,
   * so weight = 0 is how one is logged) would otherwise multiply out to 0 regardless of
   * reps, making every bodyweight session score identically instead of tracking rep
   * progress. z-scoring only cares about relative capacity changes session-to-session, so
   * this tiny floor doesn't distort anything - it just keeps reps the driver until real
   * weight gets logged, at which point the floor stops applying.
   */
  fun capacity(weight: Double, reps: Int, config: EffortConfig = EffortConfig.Default): Double =
    max(weight, 1.0) * (1 + effectiveReps(reps, config) / config.epleyDivisor)

  /**
   * Reps as the capacity scale counts them: linear up to [EffortConfig.repCap], then
   * log-compressed so a higher rep count always scores higher with diminishing returns.
   *
   * Continuous at the cap and with a matching first derivative there, so nothing about sets at
   * or under it changes - the compression only decides how much a 16th rep and beyond is worth.
   * A hard clamp scored 25 reps exactly like 15, which is fine for loadable lifts and useless
   * for bodyweight work, where reps are the only thing that can go up.
   */
  fun effectiveReps(reps: Int, config: EffortConfig = EffortConfig.Default): Double {
    val clamped = reps.coerceAtLeast(0)
    if (clamped <= config.repCap || config.repSoftCapScale <= 0.0) {
      return min(clamped, config.repCap).toDouble()
    }
    val excess = (clamped - config.repCap).toDouble()
    return config.repCap + config.repSoftCapScale * ln(1 + excess / config.repSoftCapScale)
  }

  /** [effectiveReps] for a fractional rep count - see [weightForReps]'s `Double` overload. */
  fun effectiveReps(reps: Double, config: EffortConfig = EffortConfig.Default): Double {
    val clamped = reps.coerceAtLeast(0.0)
    if (clamped <= config.repCap || config.repSoftCapScale <= 0.0) {
      return min(clamped, config.repCap.toDouble())
    }
    val excess = clamped - config.repCap
    return config.repCap + config.repSoftCapScale * ln(1 + excess / config.repSoftCapScale)
  }

  /** Inverse of [capacity]'s weight side: the weight that yields [capacity] at [effectiveReps]. */
  private fun weightForEffectiveReps(capacity: Double, effectiveReps: Double, config: EffortConfig) =
    capacity / (1 + effectiveReps / config.epleyDivisor)

  /**
   * The weight that would yield [capacity] at a literal target rep count, e.g. the funnel
   * band's heavy (low-rep) and light (high-rep) edges. Unlike the internal fit's typical-reps
   * conversion, [reps] is taken as-is through [effectiveReps] rather than a recency-weighted
   * estimate.
   */
  fun weightForReps(capacity: Double, reps: Int, config: EffortConfig = EffortConfig.Default): Double =
    weightForEffectiveReps(capacity, effectiveReps(reps, config), config)

  /**
   * [weightForReps] for a fractional rep target, e.g. [ScoredSet.lowAnchorReps]/
   * [TrendPoint.lowAnchorReps] once a stagnation streak has pulled it below the integral
   * constant it starts at.
   */
  fun weightForReps(capacity: Double, reps: Double, config: EffortConfig = EffortConfig.Default): Double =
    weightForEffectiveReps(capacity, effectiveReps(reps, config), config)

  /**
   * Reps mapped to `[0, 1]` for chart bubble size - a display-only alternative to [bubbleSize].
   * Effort's z-score clusters most working sets near "on curve," so its bubble size barely
   * varies across a real chart; reps vary far more per set and read as a visible size
   * difference at a glance. [EffortConfig.repCap] is the natural ceiling already used to cap
   * reps' contribution to capacity.
   */
  fun repSize(reps: Int, config: EffortConfig = EffortConfig.Default): Float =
    (reps.toFloat() / config.repCap).coerceIn(0f, 1f)

  fun bubbleSize(z: Double?): Float {
    if (z == null) return NEUTRAL_SIZE
    if (z <= HUMP_ANCHORS.first().first) return HUMP_ANCHORS.first().second.toFloat()
    if (z >= HUMP_ANCHORS.last().first) return HUMP_ANCHORS.last().second.toFloat()
    for (i in 0 until HUMP_ANCHORS.size - 1) {
      val (x0, y0) = HUMP_ANCHORS[i]
      val (x1, y1) = HUMP_ANCHORS[i + 1]
      if (z <= x1) {
        val t = (z - x0) / (x1 - x0)
        return (y0 + t * (y1 - y0)).toFloat()
      }
    }
    return HUMP_ANCHORS.last().second.toFloat()
  }

  fun zoneOf(z: Double?): EffortZone = when {
    z == null -> EffortZone.COLD
    z < -1.0 -> EffortZone.BELOW
    z < 0.5 -> EffortZone.ON_CURVE
    z < 2.0 -> EffortZone.GROWTH
    else -> EffortZone.IMPLAUSIBLE
  }

  /**
   * One unit the regression treats as a "session".
   *
   * [sessionIndex] and [dayOffset] are always calendar-day facts even when the fit is running
   * at set granularity, so everything downstream (session gap marks, x-axis labels) keeps
   * reading the same session structure regardless of which grain produced the numbers. [x] is
   * the regression's own domain and is the only field that differs between grains.
   *
   * [layoffDays] is the calendar gap to the previous day the lifter trained, so every fit
   * session inside one day shares it. [agingDays] is that same gap charged once per day rather
   * than once per fit session - at set grain, letting each of five sets bill a 90-day layoff
   * separately would age the history by 450 days.
   */
  private data class FitSession(
    val sets: List<TimedSet>,
    val sessionIndex: Int,
    val dayOffset: Long,
    val x: Double,
    val layoffDays: Double,
    val agingDays: Double
  )

  /**
   * A set paired with how long the lifter rested before it, in seconds - null for the first set
   * of a calendar day, which has nothing before it to rest from. Measured once at day grain so
   * both grains see the same rest, however they go on to group the sets.
   */
  private data class TimedSet(val set: EffortSet, val restSeconds: Double?)

  /** Calendar-day grain: one fit session per day, regressed on day-offset. */
  private fun daySessions(sets: List<EffortSet>, zone: ZoneId): List<FitSession> {
    val byDay = sets
      .sortedBy { it.completed }
      .groupBy { it.completed.atZone(zone).toLocalDate() }
      .toSortedMap()
    val firstDay = byDay.firstKey()
    var previousDayOffset: Long? = null
    return byDay.entries.mapIndexed { index, (day, sessionSets) ->
      val dayOffset = ChronoUnit.DAYS.between(firstDay, day)
      val layoffDays = previousDayOffset?.let { (dayOffset - it).toDouble() } ?: 0.0
      previousDayOffset = dayOffset
      val timed = sessionSets.mapIndexed { setIndex, set ->
        val rest = if (setIndex == 0) {
          null
        } else {
          Duration.between(sessionSets[setIndex - 1].completed, set.completed).toMillis() / 1000.0
        }
        TimedSet(set, rest)
      }
      FitSession(timed, index, dayOffset, dayOffset.toDouble(), layoffDays, layoffDays)
    }
  }

  /**
   * How far the expectation is cut back for time away. Ordinary rest days and a missed week sit
   * inside the grace period and cost nothing; past that it decays on its own half-life and
   * floors well short of zero.
   */
  private fun detrainFactor(layoffDays: Double, config: EffortConfig): Double {
    val excess = (layoffDays - config.detrainGraceDays).coerceAtLeast(0.0)
    if (excess <= 0.0 || config.detrainHalfLifeDays <= 0.0) return 1.0
    return 0.5.pow(excess / config.detrainHalfLifeDays).coerceAtLeast(config.detrainFloor)
  }

  /**
   * How much credit a set earns for how little rest preceded it. Bonus only, capped at
   * [EffortConfig.densityBonusMax] - see the config for why this never subtracts.
   */
  private fun densityFactor(restSeconds: Double?, config: EffortConfig): Double = when {
    restSeconds == null -> 1.0
    restSeconds < config.restImplausibleFloorSeconds -> 1.0
    restSeconds > config.restNeutralSeconds -> 1.0
    else -> {
      val span = config.restReferenceSeconds - config.restCreditFloorSeconds
      val credit = if (span <= 0.0) {
        0.0
      } else {
        ((config.restReferenceSeconds - restSeconds) / span).coerceIn(0.0, 1.0)
      }
      1.0 + config.densityBonusMax * credit
    }
  }

  private fun effectiveCapacity(timed: TimedSet, config: EffortConfig): Double =
    capacity(timed.set.weight, timed.set.reps, config) * densityFactor(timed.restSeconds, config)

  /**
   * How much of its best a session actually held, as a multiplier on the value it feeds the
   * trend. Three sets at the same weight keep all of it; a top set followed by two lighter ones
   * gives some back.
   *
   * Only sets from [peakIndex] onward count. Ramping up to a top set is warm-up, not fade, and
   * a session that ends on its best never faded at all - so a lone top single, and a session of
   * one set, are both left alone. This is deliberately a discount off [bestCapacity] rather
   * than a bonus above it: session values have to stay on the same scale as the individual set
   * capacities they get compared against, or the whole trend drifts above every real set.
   *
   * Aggregate only. Individual bubbles already show a fade on their own - the lighter sets
   * score lower against the session's expectation - so crediting it per set would count it
   * twice.
   */
  private fun sustainFactor(
    capacities: List<Double>,
    peakIndex: Int,
    bestCapacity: Double,
    config: EffortConfig
  ): Double {
    val tail = capacities.subList(peakIndex, capacities.size)
    if (tail.size <= 1 || bestCapacity <= 0.0) return 1.0
    val tolerance = config.sustainTolerance
    val span = 1.0 - tolerance
    val holdRatio = tail.sumOf { c ->
      if (span <= 0.0) {
        if (c >= bestCapacity) 1.0 else 0.0
      } else {
        ((c / bestCapacity - tolerance) / span).coerceIn(0.0, 1.0)
      }
    } / tail.size
    return 1.0 - config.sustainPenaltyMax * (1.0 - holdRatio)
  }

  /**
   * Set grain: every set is its own fit session, regressed on a running set-sequence counter.
   *
   * The x-domain stays set-sequence rather than fractional days on purpose. A day's sets span
   * about 0.02 days, so a fractional-day fit over them is near-degenerate, and extrapolating
   * that slope a full day forward to the next session runs straight into the `1.5 * meanY`
   * clamp. Set-sequence x is well conditioned, and it's the domain the strip already plots
   * against. The time-aware terms read [EffortSet.completed] deltas directly, so they behave
   * identically either way.
   */
  private fun explodedSessions(sets: List<EffortSet>, zone: ZoneId): List<FitSession> {
    var x = 0.0
    return daySessions(sets, zone).flatMap { day ->
      day.sets.mapIndexed { setIndex, timed ->
        FitSession(
          sets = listOf(timed),
          sessionIndex = day.sessionIndex,
          dayOffset = day.dayOffset,
          x = x++,
          layoffDays = day.layoffDays,
          // Charged once for the day, on its first set, so a five-set comeback session doesn't
          // age the history by five layoffs.
          agingDays = if (setIndex == 0) day.agingDays else 0.0
        )
      }
    }
  }

  /**
   * Groups [sets] into calendar-day sessions (in [zone]) and scores every set against a
   * recency-weighted linear regression of session-best capacity, fit only on sessions
   * strictly before it (causal / one-step-ahead).
   *
   * The regression's x-domain is always day-offset-from-first-session, never a caller's
   * chart x — the drawer, the exercise-page strip, and the day-exploded variant each use a
   * different x treatment, and a fit that depended on which one was rendering it would be
   * wrong.
   */
  fun score(
    sets: List<EffortSet>,
    zone: ZoneId = ZoneId.systemDefault(),
    config: EffortConfig = EffortConfig.Default
  ): EffortSeries {
    if (sets.isEmpty()) return EffortSeries.Empty
    return scoreSessions(
      daySessions(sets, zone),
      config,
      config.maxExtrapolationDays.toDouble(),
      trackStagnation = true
    )
  }

  /**
   * The shared fit. Walks [sessions] in order, predicting each from the ones strictly before it
   * and folding it in only afterwards, so a session's outcome never influences its own
   * expectation. Both grains run this same code - the only difference is what a "session" is
   * and what [FitSession.x] means.
   */
  private fun scoreSessions(
    sessions: List<FitSession>,
    config: EffortConfig,
    maxExtrapolation: Double,
    skipWarmUps: Boolean = false,
    trackStagnation: Boolean = false
  ): EffortSeries {
    if (sessions.isEmpty()) return EffortSeries.Empty

    val sessionDecay = 0.5.pow(1.0 / config.halfLifeSessions)

    // Regression accumulators (recency-weighted sums of x, y=session-best capacity).
    var sumW = 0.0
    var sumWx = 0.0
    var sumWy = 0.0
    var sumWxx = 0.0
    var sumWxy = 0.0

    // Residual-scale accumulators, folded only for sessions that themselves had a prediction.
    var sumResidualW = 0.0
    var sumResidualWe2 = 0.0

    // Typical-reps accumulators (session-best reps).
    var sumRepsW = 0.0
    var sumRepsWr = 0.0

    var priorSessionCount = 0
    var lastPriorX = 0.0
    var runningBest = 0.0

    // Stagnation tracking (day-grain only - see trackStagnation). stagnationRepsSum/Count are
    // a running average of reps across a streak of sessions whose session-best weight and reps
    // both exactly matched the one before, so it reads back as (sum + repCap) / (count + 1):
    // repCap alone with no streak, sliding toward the repeated value the longer it runs.
    var stagnationRepsSum = 0.0
    var stagnationCount = 0
    var lastStagnationWeight: Double? = null
    var lastStagnationReps: Int? = null

    val scoredSets = mutableListOf<ScoredSet>()
    val trendPoints = mutableListOf<TrendPoint>()

    sessions.forEach { session ->
      val sessionSets = session.sets

      val capacities = sessionSets.map { effectiveCapacity(it, config) }
      var peakIndex = 0
      var bestCapacity = capacities[0]
      var bestReps = effectiveReps(sessionSets[0].set.reps, config)
      var bestRawReps = sessionSets[0].set.reps
      var bestWeight = sessionSets[0].set.weight
      for (i in 1 until sessionSets.size) {
        val c = capacities[i]
        val r = effectiveReps(sessionSets[i].set.reps, config)
        if (c > bestCapacity || (c == bestCapacity && r > bestReps)) {
          peakIndex = i
          bestCapacity = c
          bestReps = r
          bestRawReps = sessionSets[i].set.reps
          bestWeight = sessionSets[i].set.weight
        }
      }
      val sessionValue = bestCapacity * sustainFactor(capacities, peakIndex, bestCapacity, config)

      // Causal, like the rest of this loop's state: the streak read here reflects sessions
      // strictly before this one, and is only updated for this session's own outcome further
      // down, after scoring.
      val lowAnchorReps = if (trackStagnation) {
        (stagnationRepsSum + config.repCap) / (stagnationCount + 1)
      } else {
        config.repCap.toDouble()
      }

      var expectedCapacity: Double? = null
      var expectedWeight: Double? = null
      var residualScale: Double? = null

      if (priorSessionCount >= config.minPriorSessions) {
        val xEff = min(session.x, lastPriorX + maxExtrapolation)
        val den = sumW * sumWxx - sumWx * sumWx
        val meanY = sumWy / sumW
        val raw = if (abs(den) < 1e-9) {
          meanY
        } else {
          val slope = (sumW * sumWxy - sumWx * sumWy) / den
          val intercept = (sumWy - slope * sumWx) / sumW
          intercept + slope * xEff
        }
        // A causal fit on only 3-4 sessions extrapolates wildly; shrink toward the
        // recency-weighted mean until enough sessions have accumulated to trust the slope.
        val lambda = ((priorSessionCount - 2) / 4.0).coerceIn(0.0, 1.0)
        val shrunk = if (config.shrinkToMean) lambda * raw + (1 - lambda) * meanY else raw
        // The extrapolation clamp above bounds how far the *slope* can run after a gap; this
        // bounds the *level*, which nothing did before - the curve used to freeze where it was
        // left, so coming back from months off read BELOW on every set until it was clawed back.
        val cHat = shrunk.coerceIn(0.5 * meanY, 1.5 * meanY) * detrainFactor(session.layoffDays, config)
        expectedCapacity = cHat

        val fittedResidualScale = if (sumResidualW >= 2.0) sqrt(sumResidualWe2 / sumResidualW) else 0.0
        residualScale = max(fittedResidualScale, max(config.residualScaleFloorFraction * cHat, 1e-6))

        val rHat = (sumRepsWr / sumRepsW).coerceIn(1.0, config.repSoftCapMax)
        val wHat = weightForEffectiveReps(cHat, rHat, config)
        expectedWeight = wHat
        trendPoints.add(
          TrendPoint(
            sessionIndex = session.sessionIndex,
            dayOffset = session.dayOffset,
            at = sessionSets.first().set.completed,
            expectedCapacity = cHat,
            typicalReps = rHat,
            expectedWeight = wHat,
            lowAnchorReps = lowAnchorReps
          )
        )
      }

      sessionSets.forEachIndexed { setIndex, timed ->
        val c = effectiveCapacity(timed, config)
        val z = expectedCapacity?.let {
          ((c - it) / (residualScale ?: 1.0)).coerceIn(-config.maxAbsZ, config.maxAbsZ)
        }
        scoredSets.add(
          ScoredSet(
            source = timed.set,
            sessionIndex = session.sessionIndex,
            setIndexInSession = setIndex,
            setsInSession = sessionSets.size,
            dayOffset = session.dayOffset,
            capacity = c,
            expectation = expectedCapacity,
            expectedWeight = expectedWeight,
            z = z,
            size = bubbleSize(z),
            zone = zoneOf(z),
            lowAnchorReps = lowAnchorReps
          )
        )
      }

      // At day grain, taking the session best already means a warm-up can never drag the fit.
      // At set grain there is no aggregation to hide behind, so the same protection has to be
      // explicit: a set far under what the lifter has already shown is a warm-up, and folding it
      // in as if it were a session outcome yanks the recency-weighted line down hard enough to
      // make the very next working set read IMPLAUSIBLE.
      val isWarmUp = skipWarmUps && sessionValue < config.workingSetFraction * runningBest
      runningBest = max(runningBest, sessionValue)
      if (isWarmUp) return@forEach

      if (trackStagnation) {
        val previousWeight = lastStagnationWeight
        if (previousWeight != null && abs(bestWeight - previousWeight) < 1e-6 &&
          bestRawReps == lastStagnationReps
        ) {
          stagnationRepsSum += bestRawReps
          stagnationCount += 1
        } else {
          stagnationRepsSum = 0.0
          stagnationCount = 0
        }
        lastStagnationWeight = bestWeight
        lastStagnationReps = bestRawReps
      }

      // Fold this session in *after* using it for prediction/scoring, so a session's
      // outcome (a PR or a bad day) never influences its own expectation.
      val decay = sessionDecay * 0.5.pow(session.agingDays / config.halfLifeDays)
      sumW *= decay; sumWx *= decay; sumWy *= decay; sumWxx *= decay; sumWxy *= decay
      sumResidualW *= decay; sumResidualWe2 *= decay
      sumRepsW *= decay; sumRepsWr *= decay

      val x = session.x
      sumW += 1.0
      sumWx += x
      sumWy += sessionValue
      sumWxx += x * x
      sumWxy += x * sessionValue

      sumRepsW += 1.0
      sumRepsWr += bestReps

      if (expectedCapacity != null) {
        val e = sessionValue - expectedCapacity
        sumResidualW += 1.0
        sumResidualWe2 += e * e
      }

      priorSessionCount += 1
      lastPriorX = x
    }

    return EffortSeries(scoredSets, trendPoints)
  }

  /**
   * Strip-only augmentation of [score]: where the real, session-best fit is still COLD (fewer
   * than [EffortConfig.minPriorSessions] prior calendar-day sessions), fills in from a second
   * run of the very same fit at set granularity - every set treated as its own session - so a
   * chart with too little history to trust a session-best fit still has something real to show
   * rather than a screen of neutral grey.
   *
   * Running the identical machinery at a finer grain, rather than a separate hand-rolled
   * regression, is what keeps the two comparable: both shrink toward the recency-weighted mean
   * and both derive a *fitted* residual scale rather than always falling back to the floor. The
   * floor-only stand-in this replaced produced systematically larger |z| than the real fit, so
   * bubble sizes and zones visibly jumped at the handoff for identical work.
   *
   * [score] itself is untouched - the drawer still sees only the session-best fit. Sets the
   * real fit already covers are passed through tagged [ExpectationSource.SESSION]; sets the
   * exploded fit covers are tagged [ExpectationSource.BOOTSTRAP]; anything neither covers stays
   * COLD. The exploded fit is causal at set grain, so it needs
   * [EffortConfig.minPriorSetsForBootstrap] strictly-prior sets - which the current session's
   * own earlier sets can supply, so a brand-new exercise warms up within its first session.
   *
   * The most recent session is forced onto the exploded fit even once the real fit has an
   * opinion on it - it's still open, so a chart showing it should react as each set is logged
   * rather than freezing to one value the session-best fit already committed to before the
   * session existed. Every earlier session, being necessarily complete, keeps using the real
   * fit as soon as it's mature enough to have one.
   */
  fun scoreWithBootstrap(
    sets: List<EffortSet>,
    zone: ZoneId = ZoneId.systemDefault(),
    config: EffortConfig = EffortConfig.Default
  ): EffortSeries {
    val real = score(sets, zone, config)
    if (real.sets.isEmpty()) return real

    val exploded = scoreSessions(
      explodedSessions(sets, zone),
      config.copy(minPriorSessions = config.minPriorSetsForBootstrap),
      // Set-sequence x, so the bound has to be denominated in sets - maxExtrapolationDays is a
      // calendar quantity and means nothing in this domain. Skipped warm-ups don't advance
      // lastPriorX while session.x keeps counting, so the gap here tracks set volume, not time.
      maxExtrapolation = config.maxExtrapolationSets,
      skipWarmUps = true
    )

    // Sets are chronological (score()/daySessions sort ascending), so the last entry belongs to
    // the most recent - the only session that can still be open.
    val liveSessionIndex = real.sets.last().sessionIndex

    // Both grains walk the same completed-sorted sets in the same order, so the two set lists
    // line up 1:1 and can be zipped by position.
    val scoredSets = real.sets.mapIndexed { index, scored ->
      when {
        scored.expectation != null && scored.sessionIndex != liveSessionIndex ->
          scored.copy(expectationSource = ExpectationSource.SESSION)
        else -> {
          val fallback = exploded.sets[index]
          if (fallback.expectation == null) {
            scored
          } else {
            scored.copy(
              expectation = fallback.expectation,
              expectedWeight = fallback.expectedWeight,
              z = fallback.z,
              size = fallback.size,
              zone = fallback.zone,
              expectationSource = ExpectationSource.BOOTSTRAP
            )
          }
        }
      }
    }

    // One trend point per calendar session either way - per-set detail rides on each
    // ScoredSet.expectedWeight instead, which is what lets the strip draw a line that moves
    // within a session rather than stepping once per day. The live session is excluded from
    // realBySession the same way it's excluded above, so it builds its trend point from its own
    // (exploded) scored sets instead of the real fit's frozen one.
    val realBySession = real.trend.associateBy { it.sessionIndex }
    val trendPoints = scoredSets
      .filter { it.expectationSource != null }
      .groupBy { it.sessionIndex }
      .toSortedMap()
      .map { (sessionIndex, sessionScored) ->
        realBySession[sessionIndex]
          ?.takeIf { sessionIndex != liveSessionIndex }
          ?.copy(expectationSource = ExpectationSource.SESSION)
          ?: sessionScored.first().let { first ->
            val cHat = first.expectation!!
            val wHat = first.expectedWeight!!
            TrendPoint(
              sessionIndex = sessionIndex,
              dayOffset = first.dayOffset,
              at = first.source.completed,
              expectedCapacity = cHat,
              typicalReps = (cHat / wHat - 1) * config.epleyDivisor,
              expectedWeight = wHat,
              expectationSource = ExpectationSource.BOOTSTRAP,
              lowAnchorReps = first.lowAnchorReps
            )
          }
      }

    return EffortSeries(scoredSets, trendPoints)
  }
}
