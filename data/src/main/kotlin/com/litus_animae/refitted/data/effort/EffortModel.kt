package com.litus_animae.refitted.data.effort

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
   */
  private data class FitSession(
    val sets: List<EffortSet>,
    val sessionIndex: Int,
    val dayOffset: Long,
    val x: Double
  )

  /** Calendar-day grain: one fit session per day, regressed on day-offset. */
  private fun daySessions(sets: List<EffortSet>, zone: ZoneId): List<FitSession> {
    val byDay = sets
      .sortedBy { it.completed }
      .groupBy { it.completed.atZone(zone).toLocalDate() }
      .toSortedMap()
    val firstDay = byDay.firstKey()
    return byDay.entries.mapIndexed { index, (day, sessionSets) ->
      val dayOffset = ChronoUnit.DAYS.between(firstDay, day)
      FitSession(sessionSets, index, dayOffset, dayOffset.toDouble())
    }
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
      day.sets.map { set ->
        FitSession(listOf(set), day.sessionIndex, day.dayOffset, x++)
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
    return scoreSessions(daySessions(sets, zone), config)
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
    skipWarmUps: Boolean = false
  ): EffortSeries {
    if (sessions.isEmpty()) return EffortSeries.Empty

    val decay = 0.5.pow(1.0 / config.halfLifeSessions)

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

    val scoredSets = mutableListOf<ScoredSet>()
    val trendPoints = mutableListOf<TrendPoint>()

    sessions.forEach { session ->
      val sessionSets = session.sets

      var bestCapacity = capacity(sessionSets[0].weight, sessionSets[0].reps, config)
      var bestReps = effectiveReps(sessionSets[0].reps, config)
      for (i in 1 until sessionSets.size) {
        val c = capacity(sessionSets[i].weight, sessionSets[i].reps, config)
        val r = effectiveReps(sessionSets[i].reps, config)
        if (c > bestCapacity || (c == bestCapacity && r > bestReps)) {
          bestCapacity = c
          bestReps = r
        }
      }

      var expectedCapacity: Double? = null
      var expectedWeight: Double? = null
      var residualScale: Double? = null

      if (priorSessionCount >= config.minPriorSessions) {
        val xEff = min(session.x, lastPriorX + config.maxExtrapolationDays)
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
        val cHat = shrunk.coerceIn(0.5 * meanY, 1.5 * meanY)
        expectedCapacity = cHat

        val fittedResidualScale = if (sumResidualW >= 2.0) sqrt(sumResidualWe2 / sumResidualW) else 0.0
        residualScale = max(fittedResidualScale, max(config.residualScaleFloorFraction * cHat, 1e-6))

        val rHat = (sumRepsWr / sumRepsW).coerceIn(1.0, config.repSoftCapMax)
        val wHat = cHat / (1 + rHat / config.epleyDivisor)
        expectedWeight = wHat
        trendPoints.add(
          TrendPoint(
            sessionIndex = session.sessionIndex,
            dayOffset = session.dayOffset,
            at = sessionSets.first().completed,
            expectedCapacity = cHat,
            typicalReps = rHat,
            expectedWeight = wHat
          )
        )
      }

      sessionSets.forEachIndexed { setIndex, effortSet ->
        val c = capacity(effortSet.weight, effortSet.reps, config)
        val z = expectedCapacity?.let {
          ((c - it) / (residualScale ?: 1.0)).coerceIn(-config.maxAbsZ, config.maxAbsZ)
        }
        scoredSets.add(
          ScoredSet(
            source = effortSet,
            sessionIndex = session.sessionIndex,
            setIndexInSession = setIndex,
            setsInSession = sessionSets.size,
            dayOffset = session.dayOffset,
            capacity = c,
            expectation = expectedCapacity,
            expectedWeight = expectedWeight,
            z = z,
            size = bubbleSize(z),
            zone = zoneOf(z)
          )
        )
      }

      // At day grain, taking the session best already means a warm-up can never drag the fit.
      // At set grain there is no aggregation to hide behind, so the same protection has to be
      // explicit: a set far under what the lifter has already shown is a warm-up, and folding it
      // in as if it were a session outcome yanks the recency-weighted line down hard enough to
      // make the very next working set read IMPLAUSIBLE.
      val isWarmUp = skipWarmUps && bestCapacity < config.workingSetFraction * runningBest
      runningBest = max(runningBest, bestCapacity)
      if (isWarmUp) return@forEach

      // Fold this session in *after* using it for prediction/scoring, so a session's
      // outcome (a PR or a bad day) never influences its own expectation.
      sumW *= decay; sumWx *= decay; sumWy *= decay; sumWxx *= decay; sumWxy *= decay
      sumResidualW *= decay; sumResidualWe2 *= decay
      sumRepsW *= decay; sumRepsWr *= decay

      val x = session.x
      sumW += 1.0
      sumWx += x
      sumWy += bestCapacity
      sumWxx += x * x
      sumWxy += x * bestCapacity

      sumRepsW += 1.0
      sumRepsWr += bestReps

      if (expectedCapacity != null) {
        val e = bestCapacity - expectedCapacity
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
      skipWarmUps = true
    )

    // Both grains walk the same completed-sorted sets in the same order, so the two set lists
    // line up 1:1 and can be zipped by position.
    val scoredSets = real.sets.mapIndexed { index, scored ->
      when {
        scored.expectation != null -> scored.copy(expectationSource = ExpectationSource.SESSION)
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
    // within a session rather than stepping once per day.
    val realBySession = real.trend.associateBy { it.sessionIndex }
    val trendPoints = scoredSets
      .filter { it.expectationSource != null }
      .groupBy { it.sessionIndex }
      .toSortedMap()
      .map { (sessionIndex, sessionScored) ->
        realBySession[sessionIndex]?.copy(expectationSource = ExpectationSource.SESSION)
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
              expectationSource = ExpectationSource.BOOTSTRAP
            )
          }
      }

    return EffortSeries(scoredSets, trendPoints)
  }
}
