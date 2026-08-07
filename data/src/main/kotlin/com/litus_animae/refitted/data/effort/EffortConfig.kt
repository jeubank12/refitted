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
  // History ages by calendar time as well as by session count. Without this, nine sessions
  // spread over three weeks weigh exactly the same as nine spread over two years. A normal
  // 3-day cadence costs about 2%, so regular training is untouched; a 90-day gap halves
  // everything before it, which is what lets the comeback session dominate the refit.
  val halfLifeDays: Double = 90.0,
  // Detraining. maxExtrapolationDays freezes the curve's level after a layoff but never lowers
  // it, so coming back from months off meant every set read BELOW until the old numbers were
  // clawed back. The expectation now decays with time away, past a grace period that ordinary
  // rest days and a missed week fall inside, and floors well short of zero - time off costs
  // something, not everything. Applied to the prediction only: the comeback session still folds
  // in at its true capacity, so the curve re-anchors to reality rather than to the haircut.
  val detrainGraceDays: Double = 10.0,
  // The grace above is a floor, not the whole story: it also scales with the cadence this
  // exercise is actually trained on. A flat calendar constant made an exercise's own rotation
  // look like detraining - alternating pull-ups and pull-downs leaves 14 days between sessions
  // of each, so both sat permanently past a 10-day grace and a *flat plateau* on that rotation
  // scored GROWTH. At 1.5x, a 14-day rotation gets 21 days free and a 28-day one gets 42, while
  // a gap genuinely abnormal for the exercise still detrains. Zero restores the flat constant.
  val cadenceGraceMultiple: Double = 1.5,
  val detrainHalfLifeDays: Double = 60.0,
  val detrainFloor: Double = 0.6,
  // How much of that haircut is also charged as uncertainty. Cutting the expectation without
  // widening the band around it makes the model *more* confident the longer someone has been
  // away - the residual floor is a fraction of the expectation, so it shrinks right along with
  // it - and a comeback then reads IMPLAUSIBLE for the crime of being predictable. Only ever
  // non-zero when detrainFactor is, so every session inside the grace period is untouched.
  val layoffUncertaintyWeight: Double = 0.75,
  val minPriorSessions: Int = 3,
  val residualScaleFloorFraction: Double = 0.05,
  val maxExtrapolationDays: Long = 14,
  // The same bound for the set-grain bootstrap fit, whose x is a set-sequence counter rather
  // than a day offset - the two are not interchangeable. Consecutive folded sets sit 1 apart so
  // this normally never binds; it exists for the case where a run of warm-ups is skipped and the
  // next working set would otherwise be evaluated well past the last x the fit has data at.
  val maxExtrapolationSets: Double = 3.0,
  val shrinkToMean: Boolean = true,
  val maxAbsZ: Double = 6.0,
  // Rest-gap credit. Sets taken closer together demonstrate more than the same weight and reps
  // taken far apart, and for bodyweight work it's the only lever left once reps stop being
  // practical. Deliberately a bonus and never a penalty: training slowly, or getting
  // interrupted, isn't a failure, and completion stamps are too noisy to punish anyone over.
  // Full credit at or under restCreditFloorSeconds, tapering to nothing at
  // restReferenceSeconds. Gaps outside [restImplausibleFloorSeconds, restNeutralSeconds] score
  // neutral rather than extrapolating - under the floor is bulk logging or several unsaved sets
  // sharing one Instant.now(), over the ceiling is a break rather than a rest.
  val restReferenceSeconds: Double = 180.0,
  val restCreditFloorSeconds: Double = 30.0,
  val restImplausibleFloorSeconds: Double = 10.0,
  val restNeutralSeconds: Double = 900.0,
  val densityBonusMax: Double = 0.07,
  // How much a session that fades away from its best set is discounted when it feeds the trend.
  // Session value used to be the plain best, so three sets at the same weight and a single top
  // set followed by two lighter ones pushed the curve up by exactly the same amount. Only sets
  // at or after the peak count toward holding - a ramp up to a top set is a warm-up, not a fade.
  // A discount off the best rather than a bonus above it, so session values stay on the same
  // scale as the individual set capacities they're compared against. The tolerance is wide
  // enough to grade a fade rather than just detect one: a normal rep drop-off (10/8/6 at one
  // weight) gives back about 2%, while dropping the weight itself gives back the better part of
  // the maximum. A narrow band collapses both to the same discount.
  val sustainTolerance: Double = 0.80,
  val sustainPenaltyMax: Double = 0.08,
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
