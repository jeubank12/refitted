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
  // Reps past repCap keep earning credit, just log-compressed. A hard clamp meant a 25-rep set
  // scored identically to a 15-rep one, which left bodyweight and other unloadable work with no
  // progression lever at all once it cleared 15. Scale sets how fast the compression bites:
  // at 10.0, 20 reps counts as 19.1 and 25 as 21.9. Zero restores the hard clamp.
  val repSoftCapScale: Double = 10.0,
  // Upper bound on the fit's typical-reps estimate, which the hard clamp used to supply.
  val repSoftCapMax: Double = 40.0,
  val epleyDivisor: Double = 30.0,
  val halfLifeSessions: Double = 9.0,
  val minPriorSessions: Int = 3,
  val residualScaleFloorFraction: Double = 0.05,
  val maxExtrapolationDays: Long = 14,
  val shrinkToMean: Boolean = true,
  val maxAbsZ: Double = 6.0,
  // Only consulted by EffortModel.scoreWithBootstrap - how many individual sets are needed
  // before its coarser, strip-only stand-in trend can render at all. Mirrors
  // minPriorSessions' role for the real fit.
  val minPriorSetsForBootstrap: Int = 3,
  // Also bootstrap-only: the fraction of already-demonstrated capacity a set has to reach
  // before it counts as working rather than warm-up. The session-best fit gets this for free
  // by aggregating; a set-granularity fit has to say it out loud.
  val workingSetFraction: Double = 0.85
) {
  companion object {
    val Default = EffortConfig()
  }
}
