package com.litus_animae.refitted.data.effort

import java.time.Instant

enum class EffortZone { COLD, BELOW, ON_CURVE, GROWTH, IMPLAUSIBLE }

/**
 * Which regression produced a non-null expectation: [SESSION] is [EffortModel.score]'s real,
 * session-best fit; [BOOTSTRAP] is [EffortModel.scoreWithBootstrap]'s strip-only, per-set
 * stand-in used while there isn't enough session history to trust the real one yet. `null`
 * on the [ScoredSet]/[TrendPoint] this decorates means no expectation exists at all (COLD).
 */
enum class ExpectationSource { SESSION, BOOTSTRAP }

/**
 * One completed set, scored against its session's expectation.
 *
 * [dayOffset] and [sessionIndex]/[setIndexInSession]/[setsInSession] are exposed rather than
 * left implicit so a chart can build whichever x-domain it needs (calendar time, day-exploded,
 * or plain set index) without re-deriving session structure from raw [EffortSet]s.
 *
 * [capacity] is what the model scores on, so it includes the rest-gap credit a set earned for
 * following closely on the one before it - it is not the bare [EffortModel.capacity] of the
 * weight and reps alone.
 *
 * [expectedWeight] is [expectation] converted into weight *on this set's own terms* - its rep
 * count and the rest that preceded it - not at the fit's session-wide typical reps the way
 * [TrendPoint] does it. A chart whose y-axis is raw weight can only compare a dot against a
 * line drawn this way; against a typical-reps line, every set whose reps differ from the
 * average is misplaced, and a set can render below the line while scoring above it.
 *
 * [weightScale] is the conversion that produced it - what one pound of this set is worth in
 * capacity - exposed so a caller can put [residualScale] into the same units:
 *
 * ```
 * (source.weight - expectedWeight) / (residualScale / weightScale) == z
 * ```
 *
 * exactly, for any set at or above 1 lb whose [z] is not clamped. That identity is what lets a
 * band drawn at the zone thresholds *be* the zone boundaries rather than a decoration beside
 * them. Bodyweight sets are the exception by construction: [EffortModel.capacity] floors weight
 * at 1.0 so rep progress still registers, so a set logged at 0 plots below a line that cannot
 * reach it.
 */
data class ScoredSet(
  val source: EffortSet,
  val sessionIndex: Int,
  val setIndexInSession: Int,
  val setsInSession: Int,
  val dayOffset: Long,
  val capacity: Double,
  val expectation: Double?,
  val expectedWeight: Double? = null,
  val residualScale: Double? = null,
  val weightScale: Double = 1.0,
  val z: Double?,
  val zone: EffortZone,
  val expectationSource: ExpectationSource? = null
)

/**
 * The same expectation this set was scored against, re-expressed as the weight that would meet
 * it at [reps] instead of the reps this set actually used - the question a lifter about to load
 * a bar is asking, when the program prescribes a rep count they have not done yet.
 *
 * The rest that preceded this set is assumed to repeat, since the next set is normally taken on
 * the same clock. `null` whenever this set has no expectation.
 *
 * Strictly a weight-space conversion: [expectation] itself is untouched, because a set is scored
 * on the capacity it actually demonstrated and a program's target has no business changing that.
 * Doing more reps than prescribed is progress, not a miss.
 */
fun ScoredSet.expectedWeightAt(reps: Int, config: EffortConfig = EffortConfig.Default): Double? {
  val expectedCapacity = expectation ?: return null
  val here = expectedWeight ?: return null
  // weightScale bundles this set's rep multiplier with the rest credit it earned. Dividing the
  // rep part back out leaves the density factor on its own, so only the reps are re-stated.
  val repPart = 1 + EffortModel.effectiveReps(source.reps, config) / config.epleyDivisor
  val density = weightScale / repPart
  // expectedWeight is an added weight, so whatever the movement already carries is the gap
  // between it and the unadjusted conversion - see EffortConfig.bodyweightBaselineLoad.
  val baselineLoad = expectedCapacity / weightScale - here
  val target = (1 + EffortModel.effectiveReps(reps, config) / config.epleyDivisor) * density
  return expectedCapacity / target - baselineLoad
}

/** [residualScale] in the weight units [expectedWeightAt] returns for the same [reps]. */
fun ScoredSet.bandHalfWidthAt(reps: Int, config: EffortConfig = EffortConfig.Default): Double? {
  val scale = residualScale ?: return null
  val repPart = 1 + EffortModel.effectiveReps(source.reps, config) / config.epleyDivisor
  val density = weightScale / repPart
  return scale / ((1 + EffortModel.effectiveReps(reps, config) / config.epleyDivisor) * density)
}

/**
 * The fitted expectation for one session, in both capacity and weight-at-typical-reps form.
 *
 * [expectedWeight] is *added* weight, on the same axis the sets are logged and plotted on, so
 * on an unloadable movement it can legitimately be negative - the trend rising toward zero is
 * how "you are not yet ready to hang a plate on this" looks.
 */
data class TrendPoint(
  val sessionIndex: Int,
  val dayOffset: Long,
  val at: Instant,
  val expectedCapacity: Double,
  val typicalReps: Double,
  val expectedWeight: Double,
  val expectationSource: ExpectationSource? = null
)

data class EffortSeries(
  val sets: List<ScoredSet>,
  val trend: List<TrendPoint>
) {
  companion object {
    val Empty = EffortSeries(emptyList(), emptyList())
  }
}
