package com.litus_animae.refitted.data.effort

import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
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
   * How long a gap has to be, for this exercise, before it counts as time away at all.
   *
   * A flat calendar constant treats an exercise's own rotation as detraining: alternating
   * pull-ups and pull-downs puts every session 14 days after the last, so both were permanently
   * scored as partly detrained and a *flat plateau* on that rotation read GROWTH - zero progress
   * reported as growth, which defeats the point of the chart. Scaling the grace by the cadence
   * the lifter has actually established makes a normal-for-you gap free while a gap that is
   * abnormal *for this exercise* still detrains.
   */
  private fun graceDays(typicalGapDays: Double, config: EffortConfig): Double =
    max(config.detrainGraceDays, config.cadenceGraceMultiple * typicalGapDays)

  /**
   * How far the expectation is cut back for time away. Ordinary rest days, a missed week, and
   * anything inside this exercise's own rhythm sit within [graceDays] and cost nothing; past
   * that it decays on its own half-life and floors well short of zero.
   */
  private fun detrainFactor(
    layoffDays: Double,
    typicalGapDays: Double,
    config: EffortConfig
  ): Double {
    val excess = (layoffDays - graceDays(typicalGapDays, config)).coerceAtLeast(0.0)
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
    return scoreSessions(daySessions(sets, zone), config, config.maxExtrapolationDays.toDouble())
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
    skipWarmUps: Boolean = false
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

    // Typical-gap accumulators, so the detrain grace can scale with the cadence this exercise
    // is actually trained on. The very first session has nothing before it and contributes no
    // gap, which is why this is weighted separately from the regression's own sumW.
    //
    // Accumulated in log space, so the estimate is a geometric mean. Two reasons: gaps are a
    // ratio quantity (twice as long is the meaningful step, not a day longer), and a single
    // enormous outlier has to not swallow the estimate - ten 4-day gaps and one 730-day one
    // average arithmetically to 70 days, and geometrically to 6.4. That robustness is what
    // lets the layoff itself stay in the average instead of needing to be classified out,
    // which is circular: the classification is what the average is for.
    var sumGapW = 0.0
    var sumGapWlog = 0.0

    var priorSessionCount = 0
    var lastPriorX = 0.0
    var runningBest = 0.0

    val scoredSets = mutableListOf<ScoredSet>()
    val trendPoints = mutableListOf<TrendPoint>()

    sessions.forEach { session ->
      val sessionSets = session.sets

      val capacities = sessionSets.map { effectiveCapacity(it, config) }
      var peakIndex = 0
      var bestCapacity = capacities[0]
      var bestReps = effectiveReps(sessionSets[0].set.reps, config)
      for (i in 1 until sessionSets.size) {
        val c = capacities[i]
        val r = effectiveReps(sessionSets[i].set.reps, config)
        if (c > bestCapacity || (c == bestCapacity && r > bestReps)) {
          peakIndex = i
          bestCapacity = c
          bestReps = r
        }
      }
      val sessionValue = bestCapacity * sustainFactor(capacities, peakIndex, bestCapacity, config)

      // Hoisted out of the prediction block: the fold-in below needs the same verdict on
      // whether this gap was time away or just this exercise's rhythm.
      val typicalGapDays = if (sumGapW > 0.0) exp(sumGapWlog / sumGapW) else 0.0
      val detrain = detrainFactor(session.layoffDays, typicalGapDays, config)
      val followedLayoff = detrain < 1.0
      // Both halves of the comeback treatment turn on this one condition. They are a pair:
      // sliding x without re-levelling parks the old, higher block right beside a lower
      // comeback and fits a cliff through them, which is worse than leaving the gap alone.
      // A prediction has to exist for the re-level to have anything to measure against, so
      // the slide is gated on the same prior-session count rather than on sumW alone.
      val reAnchorComeback = followedLayoff && priorSessionCount >= config.minPriorSessions

      // Close the dead part of the gap in the regression's x-domain before predicting from it.
      //
      // Nothing happened during a layoff, but the days still counted, and x is a day offset -
      // so the pre-layoff block sat hundreds of days to the left of the comeback with a much
      // higher capacity, and even at the 0.4% weight the aging decay leaves it, that lever arm
      // dominated the fit. The slope came out *negative* for ten sessions while the lifter was
      // gaining every session. A translation moves every accumulated x by the same amount, so
      // the slope is preserved exactly and only the arm goes away. Paired with the re-level at
      // fold-in below: sliding alone is far worse than doing nothing, because it parks the old
      // high capacity immediately left of a much lower comeback.
      if (reAnchorComeback) {
        val dead = (session.layoffDays - graceDays(typicalGapDays, config)).coerceAtLeast(0.0)
        // sumWxx consumes the pre-shift sumWx, so it has to be updated first.
        sumWxx += 2 * dead * sumWx + dead * dead * sumW
        sumWx += dead * sumW
        sumWxy += dead * sumWy
        lastPriorX += dead
      }

      var expectedCapacity: Double? = null
      var expectedWeight: Double? = null
      var residualScale: Double? = null
      // The expectation before the detrain haircut - what the fit says you'd be at had you
      // never stopped. The fold-in measures the comeback against it to size the real handicap.
      var undetrainedCapacity: Double? = null

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
        val undetrained = shrunk.coerceIn(0.5 * meanY, 1.5 * meanY)
        undetrainedCapacity = undetrained
        val cHat = undetrained * detrain
        expectedCapacity = cHat

        val fittedResidualScale = if (sumResidualW >= 2.0) sqrt(sumResidualWe2 / sumResidualW) else 0.0
        val fittedOrFloor = max(fittedResidualScale, max(config.residualScaleFloorFraction * cHat, 1e-6))
        // Detraining lowers the bar but says nothing about how well that lower number is known,
        // and the floor is a fraction of it - so the haircut used to tighten the band along with
        // the target it was cutting, and an ordinary comeback scored several sigma above a number
        // the model had just admitted it couldn't predict. The haircut is itself the scale of what
        // isn't known, so it widens the band instead of narrowing it.
        val layoffUncertainty = config.layoffUncertaintyWeight * (undetrained - cHat)
        residualScale = sqrt(fittedOrFloor * fittedOrFloor + layoffUncertainty * layoffUncertainty)

        val rHat = (sumRepsWr / sumRepsW).coerceIn(1.0, config.repSoftCapMax)
        val wHat = cHat / (1 + rHat / config.epleyDivisor)
        expectedWeight = wHat
        trendPoints.add(
          TrendPoint(
            sessionIndex = session.sessionIndex,
            dayOffset = session.dayOffset,
            at = sessionSets.first().set.completed,
            expectedCapacity = cHat,
            typicalReps = rHat,
            expectedWeight = wHat
          )
        )
      }

      sessionSets.forEachIndexed { setIndex, timed ->
        val c = effectiveCapacity(timed, config)
        val z = expectedCapacity?.let {
          ((c - it) / (residualScale ?: 1.0)).coerceIn(-config.maxAbsZ, config.maxAbsZ)
        }
        // What one pound is worth to *this* set - its reps and the rest it followed, together.
        // Dividing the expectation by it converts a capacity into the weight that would meet it
        // at exactly this set's terms, which is the only version a chart plotting raw weight can
        // compare a dot against. The session-wide, typical-reps conversion misplaces every set
        // whose reps differ from the average, which is most of them.
        val setScale = c / max(timed.set.weight, 1.0)
        scoredSets.add(
          ScoredSet(
            source = timed.set,
            sessionIndex = session.sessionIndex,
            setIndexInSession = setIndex,
            setsInSession = sessionSets.size,
            dayOffset = session.dayOffset,
            capacity = c,
            expectation = expectedCapacity,
            expectedWeight = expectedCapacity?.let { it / setScale },
            residualScale = residualScale,
            weightScale = setScale,
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
      val isWarmUp = skipWarmUps && sessionValue < config.workingSetFraction * runningBest
      runningBest = max(runningBest, sessionValue)
      if (isWarmUp) return@forEach

      // A layoff's real cost is only knowable once the lifter is back, so measure it rather
      // than keeping the time-based guess: the ratio of what they actually did to what the fit
      // said they'd be at is the handicap. Rescaling the retained history by it re-expresses
      // the whole block at the level they returned to, which is what lets the slope carried
      // across the gap above describe a rebuild instead of a decline.
      //
      // Clamped to the same floor the blind guess uses, so a session that is nothing but one
      // light feeler set cannot re-level a whole history down to it, and to 1.0, because
      // coming back strong is already captured by folding this session in at its true value -
      // it must not retroactively inflate what was done years ago.
      val handicap = undetrainedCapacity
        ?.takeIf { reAnchorComeback }
        ?.let { (sessionValue / it).coerceIn(config.detrainFloor, 1.0) }
        ?: 1.0

      // Fold this session in *after* using it for prediction/scoring, so a session's
      // outcome (a PR or a bad day) never influences its own expectation.
      //
      // A re-levelled history skips the calendar term: aging is what strips a stale block of
      // influence, and once the block has been restated at today's level it is no longer
      // stale - it is the only evidence of how fast this lifter improves. The session-count
      // half-life still retires it over the next several sessions as real post-comeback data
      // arrives, so it acts as a prior rather than an anchor.
      val decay = if (reAnchorComeback) {
        sessionDecay
      } else {
        sessionDecay * 0.5.pow(session.agingDays / config.halfLifeDays)
      }
      sumW *= decay; sumWx *= decay; sumWy *= decay; sumWxx *= decay; sumWxy *= decay
      sumResidualW *= decay; sumResidualWe2 *= decay
      sumRepsW *= decay; sumRepsWr *= decay

      // Level, not shape: only the capacity-valued sums move, so the fitted slope keeps its
      // proportions and simply lands where the lifter actually is.
      sumWy *= handicap; sumWxy *= handicap
      sumResidualWe2 *= handicap * handicap
      // Session-count decay only, deliberately skipping the calendar term the others carry.
      // Cadence is a per-exposure property, and aging it by the very gap being judged is
      // self-defeating: a two-year break would age away the rhythm it needs to be measured
      // against, leaving the break itself as the only sample and its own new "normal".
      sumGapW *= sessionDecay; sumGapWlog *= sessionDecay

      val x = session.x
      sumW += 1.0
      sumWx += x
      sumWy += sessionValue
      sumWxx += x * x
      sumWxy += x * sessionValue

      sumRepsW += 1.0
      sumRepsWr += bestReps

      // Read off agingDays rather than layoffDays so the gap is charged once per calendar day
      // at either grain - at set granularity every set of a day shares the day's layoff, and
      // billing each of them would report a five-set comeback as five gaps and inflate the
      // cadence the grace is derived from. Zero means "no day preceded this", never a real gap:
      // distinct sorted days are always at least one apart.
      if (session.agingDays > 0.0) {
        sumGapW += 1.0
        sumGapWlog += ln(session.agingDays)
      }

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
              residualScale = fallback.residualScale,
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
            // Read the reps straight off the set rather than inverting its expectedWeight:
            // that conversion now also carries the set's rest credit, so backing reps out of
            // it would report a densely-packed session as having done more reps than it did.
            // TrendPoint stays a typical-reps quantity, so its own reconstruction still holds.
            val rHat = effectiveReps(first.source.reps, config)
            TrendPoint(
              sessionIndex = sessionIndex,
              dayOffset = first.dayOffset,
              at = first.source.completed,
              expectedCapacity = cHat,
              typicalReps = rHat,
              expectedWeight = cHat / (1 + rHat / config.epleyDivisor),
              expectationSource = ExpectationSource.BOOTSTRAP
            )
          }
      }

    return EffortSeries(scoredSets, trendPoints)
  }
}
