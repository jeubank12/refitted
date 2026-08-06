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
 * [expectedWeight] is [expectation] expressed at the fit's typical reps, the same conversion
 * [TrendPoint] carries. It's per-set rather than per-session so a chart plotting one point per
 * set can draw a trend that moves across a session instead of stepping once per day.
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
  val z: Double?,
  val size: Float,
  val zone: EffortZone,
  val expectationSource: ExpectationSource? = null
)

/** The fitted expectation for one session, in both capacity and weight-at-typical-reps form. */
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
