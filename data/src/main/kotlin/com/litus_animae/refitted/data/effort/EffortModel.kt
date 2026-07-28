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

  /** Epley-style estimated 1RM, the single scale weight×reps sets are compared on. */
  fun capacity(weight: Double, reps: Int, config: EffortConfig = EffortConfig.Default): Double {
    val clampedReps = reps.coerceIn(0, config.repCap)
    return max(weight, 0.0) * (1 + clampedReps.toDouble() / config.epleyDivisor)
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
}
