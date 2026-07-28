package com.litus_animae.refitted.data.effort

/**
 * Tunables for [EffortModel]. See `docs/exercise-history-chart.md` for the design this
 * implements.
 *
 * [residualScaleFloorFraction] intentionally deviates from that doc's 2.5%: at 2.5% the
 * floor only ever binds on a near-flat history, and there it makes a small, plausible
 * improvement (e.g. +5 lb on a plateau) score as "implausibly far above" and get punished
 * — the opposite of what an adaptive curve should reward. 5% keeps that case in the
 * growth zone; see the worked-examples test for the numbers this changes.
 */
data class EffortConfig(
  val repCap: Int = 15,
  val epleyDivisor: Double = 30.0,
  val halfLifeSessions: Double = 9.0,
  val minPriorSessions: Int = 3,
  val residualScaleFloorFraction: Double = 0.05,
  val maxExtrapolationDays: Long = 14,
  val shrinkToMean: Boolean = true,
  val maxAbsZ: Double = 6.0
) {
  companion object {
    val Default = EffortConfig()
  }
}
