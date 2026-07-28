package com.litus_animae.refitted.data.effort

import java.time.Instant

enum class EffortZone { COLD, BELOW, ON_CURVE, GROWTH, IMPLAUSIBLE }

/**
 * One completed set, scored against its session's expectation.
 *
 * [dayOffset] and [sessionIndex]/[setIndexInSession]/[setsInSession] are exposed rather than
 * left implicit so a chart can build whichever x-domain it needs (calendar time, day-exploded,
 * or plain set index) without re-deriving session structure from raw [EffortSet]s.
 */
data class ScoredSet(
  val source: EffortSet,
  val sessionIndex: Int,
  val setIndexInSession: Int,
  val setsInSession: Int,
  val dayOffset: Long,
  val capacity: Double,
  val expectation: Double?,
  val z: Double?,
  val size: Float,
  val zone: EffortZone
)

/** The fitted expectation for one session, in both capacity and weight-at-typical-reps form. */
data class TrendPoint(
  val sessionIndex: Int,
  val dayOffset: Long,
  val at: Instant,
  val expectedCapacity: Double,
  val typicalReps: Double,
  val expectedWeight: Double
)

data class EffortSeries(
  val sets: List<ScoredSet>,
  val trend: List<TrendPoint>
) {
  companion object {
    val Empty = EffortSeries(emptyList(), emptyList())
  }
}
