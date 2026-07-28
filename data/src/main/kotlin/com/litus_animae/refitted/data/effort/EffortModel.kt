package com.litus_animae.refitted.data.effort

import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Scores sets against an adaptive, causally-fit expectation of demonstrated capacity.
 * See `docs/exercise-history-chart.md` for the design this implements.
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
  fun capacity(weight: Double, reps: Int, config: EffortConfig = EffortConfig.Default): Double {
    val clampedReps = reps.coerceIn(0, config.repCap)
    return max(weight, 1.0) * (1 + clampedReps.toDouble() / config.epleyDivisor)
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

    val sessions = sets
      .sortedBy { it.completed }
      .groupBy { it.completed.atZone(zone).toLocalDate() }
      .toSortedMap()
      .values
      .toList()

    val firstDay = sessions.first().first().completed.atZone(zone).toLocalDate()
    val decay = 0.5.pow(1.0 / config.halfLifeSessions)

    // Regression accumulators (recency-weighted sums of x=dayOffset, y=session-best capacity).
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
    var lastPriorDayOffset = 0L

    val scoredSets = mutableListOf<ScoredSet>()
    val trendPoints = mutableListOf<TrendPoint>()

    sessions.forEachIndexed { sessionIndex, sessionSets ->
      val day = sessionSets.first().completed.atZone(zone).toLocalDate()
      val dayOffset = ChronoUnit.DAYS.between(firstDay, day)

      var bestCapacity = capacity(sessionSets[0].weight, sessionSets[0].reps, config)
      var bestReps = sessionSets[0].reps.coerceIn(0, config.repCap)
      for (i in 1 until sessionSets.size) {
        val c = capacity(sessionSets[i].weight, sessionSets[i].reps, config)
        val r = sessionSets[i].reps.coerceIn(0, config.repCap)
        if (c > bestCapacity || (c == bestCapacity && r > bestReps)) {
          bestCapacity = c
          bestReps = r
        }
      }

      var expectedCapacity: Double? = null
      var residualScale: Double? = null

      if (priorSessionCount >= config.minPriorSessions) {
        val xEff = min(dayOffset, lastPriorDayOffset + config.maxExtrapolationDays).toDouble()
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

        val rHat = (sumRepsWr / sumRepsW).coerceIn(1.0, config.repCap.toDouble())
        val wHat = cHat / (1 + rHat / config.epleyDivisor)
        trendPoints.add(
          TrendPoint(
            sessionIndex = sessionIndex,
            dayOffset = dayOffset,
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
            sessionIndex = sessionIndex,
            setIndexInSession = setIndex,
            setsInSession = sessionSets.size,
            dayOffset = dayOffset,
            capacity = c,
            expectation = expectedCapacity,
            z = z,
            size = bubbleSize(z),
            zone = zoneOf(z)
          )
        )
      }

      // Fold this session in *after* using it for prediction/scoring, so a session's
      // outcome (a PR or a bad day) never influences its own expectation.
      sumW *= decay; sumWx *= decay; sumWy *= decay; sumWxx *= decay; sumWxy *= decay
      sumResidualW *= decay; sumResidualWe2 *= decay
      sumRepsW *= decay; sumRepsWr *= decay

      val x = dayOffset.toDouble()
      sumW += 1.0
      sumWx += x
      sumWy += bestCapacity
      sumWxx += x * x
      sumWxy += x * bestCapacity

      sumRepsW += 1.0
      sumRepsWr += bestReps.toDouble()

      if (expectedCapacity != null) {
        val e = bestCapacity - expectedCapacity
        sumResidualW += 1.0
        sumResidualWe2 += e * e
      }

      priorSessionCount += 1
      lastPriorDayOffset = dayOffset
    }

    return EffortSeries(scoredSets, trendPoints)
  }

  /**
   * Strip-only augmentation of [score]: for a session still COLD under the real, session-best
   * fit (fewer than [EffortConfig.minPriorSessions] prior sessions), fits a second, coarser
   * regression over individual set capacities from strictly prior sessions instead of
   * session-bests, so a chart with too little history to trust a session-best fit still has
   * something real to show rather than a screen of neutral grey. [score] itself is untouched
   * by this - the doc spec's cold-start rule and every session at or past
   * [EffortConfig.minPriorSessions] behave identically to calling [score] directly, just
   * tagged [ExpectationSource.SESSION] instead of left `null`.
   *
   * Gated on [EffortConfig.minPriorSetsForBootstrap] sets accumulated from sessions strictly
   * before the one being scored - never the current session's own sets, so logging a set never
   * moves that same set's own comparison value (the same causal, fold-in-after-scoring
   * discipline [score] uses for sessions, just at set granularity). Concretely: a first-ever
   * session is always all-COLD no matter how many sets it has, since no prior session exists
   * yet to bootstrap from.
   */
  fun scoreWithBootstrap(
    sets: List<EffortSet>,
    zone: ZoneId = ZoneId.systemDefault(),
    config: EffortConfig = EffortConfig.Default
  ): EffortSeries {
    val real = score(sets, zone, config)
    if (real.sets.isEmpty()) return real

    val sessions = sets
      .sortedBy { it.completed }
      .groupBy { it.completed.atZone(zone).toLocalDate() }
      .toSortedMap()
      .values
      .toList()

    val decay = 0.5.pow(1.0 / config.halfLifeSessions)

    // Recency-weighted causal regression over individual prior-session set capacities - same
    // shrink-to-mean shape as score()'s session-best regression, just keyed by a running
    // set-sequence x instead of day-offset, and fit only on sets from sessions strictly
    // before the one it predicts for.
    var sumW = 0.0
    var sumWx = 0.0
    var sumWy = 0.0
    var sumWxx = 0.0
    var sumWxy = 0.0
    var sumRepsW = 0.0
    var sumRepsWr = 0.0
    var priorSetCount = 0
    var x = 0.0

    var realIndex = 0
    val scoredSets = mutableListOf<ScoredSet>()
    val trendPoints = mutableListOf<TrendPoint>()

    sessions.forEach { sessionSets ->
      val firstReal = real.sets[realIndex]
      val useBootstrap = firstReal.expectation == null &&
        priorSetCount >= config.minPriorSetsForBootstrap

      if (useBootstrap) {
        val meanY = sumWy / sumW
        val den = sumW * sumWxx - sumWx * sumWx
        val cHat = if (abs(den) < 1e-9) {
          meanY
        } else {
          val slope = (sumW * sumWxy - sumWx * sumWy) / den
          val intercept = (sumWy - slope * sumWx) / sumW
          (intercept + slope * x).coerceIn(0.5 * meanY, 1.5 * meanY)
        }
        // No fitted residual pass here - too little data yet to trust one - always the floor.
        val residualScale = max(config.residualScaleFloorFraction * cHat, 1e-6)
        val rHat = (sumRepsWr / sumRepsW).coerceIn(1.0, config.repCap.toDouble())
        val wHat = cHat / (1 + rHat / config.epleyDivisor)

        trendPoints.add(
          TrendPoint(
            sessionIndex = firstReal.sessionIndex,
            dayOffset = firstReal.dayOffset,
            at = sessionSets.first().completed,
            expectedCapacity = cHat,
            typicalReps = rHat,
            expectedWeight = wHat,
            expectationSource = ExpectationSource.BOOTSTRAP
          )
        )

        sessionSets.forEach { effortSet ->
          val c = capacity(effortSet.weight, effortSet.reps, config)
          val z = ((c - cHat) / residualScale).coerceIn(-config.maxAbsZ, config.maxAbsZ)
          scoredSets.add(
            real.sets[realIndex].copy(
              expectation = cHat,
              z = z,
              size = bubbleSize(z),
              zone = zoneOf(z),
              expectationSource = ExpectationSource.BOOTSTRAP
            )
          )
          realIndex++
        }
      } else {
        sessionSets.forEach { _ ->
          val scored = real.sets[realIndex]
          scoredSets.add(
            if (scored.expectation != null) {
              scored.copy(expectationSource = ExpectationSource.SESSION)
            } else {
              scored
            }
          )
          realIndex++
        }
        real.trend.firstOrNull { it.sessionIndex == firstReal.sessionIndex }?.let {
          trendPoints.add(it.copy(expectationSource = ExpectationSource.SESSION))
        }
      }

      // Fold in after scoring, same discipline as score() - a session's own sets never move
      // its own bootstrap value.
      sumW *= decay; sumWx *= decay; sumWy *= decay; sumWxx *= decay; sumWxy *= decay
      sumRepsW *= decay; sumRepsWr *= decay
      sessionSets.forEach { effortSet ->
        val c = capacity(effortSet.weight, effortSet.reps, config)
        sumW += 1.0
        sumWx += x
        sumWy += c
        sumWxx += x * x
        sumWxy += x * c
        sumRepsW += 1.0
        sumRepsWr += effortSet.reps.coerceIn(0, config.repCap).toDouble()
        priorSetCount += 1
        x += 1.0
      }
    }

    return EffortSeries(scoredSets, trendPoints.sortedBy { it.sessionIndex })
  }
}
