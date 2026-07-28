package com.litus_animae.refitted.data.effort

/**
 * Tunables for [EffortModel]'s adaptive, causally-fit expectation of demonstrated capacity.
 *
 * [residualScaleFloorFraction] intentionally deviates from the original 2.5% design target:
 * at 2.5% the
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
  val maxAbsZ: Double = 6.0,
  // Only consulted by EffortModel.scoreWithBootstrap - how many individual sets from
  // strictly prior sessions are needed before its coarser, strip-only stand-in trend can
  // render at all. Mirrors minPriorSessions' role for the real fit.
  val minPriorSetsForBootstrap: Int = 3
) {
  companion object {
    val Default = EffortConfig()
  }
}
