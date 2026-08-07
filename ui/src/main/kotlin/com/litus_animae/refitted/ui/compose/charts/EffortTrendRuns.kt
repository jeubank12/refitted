package com.litus_animae.refitted.ui.compose.charts

import com.litus_animae.refitted.data.effort.EffortModel
import com.litus_animae.refitted.data.effort.ExpectationSource
import com.litus_animae.refitted.data.effort.ScoredSet
import com.litus_animae.refitted.data.effort.bandHalfWidthAt
import com.litus_animae.refitted.data.effort.expectedWeightAt

/**
 * This set's slice of the expectation band, in weight, or `null` if it has no expectation yet.
 *
 * The edges are the zone thresholds converted through the set's own weight scale, which is what
 * makes a bubble's position against the band identical to the zone its size shows - see
 * [ScoredSet.weightScale] for the identity that guarantees it.
 */
fun ScoredSet.bandAt(x: Float): EffortBand? {
  val center = expectedWeight ?: return null
  val scale = residualScale ?: return null
  val half = scale / weightScale
  return EffortBand(
    x = x,
    lower = (center + EffortModel.UNDER_EDGE_Z * half).toFloat(),
    center = center.toFloat(),
    upper = (center + EffortModel.OVER_EDGE_Z * half).toFloat(),
    reps = source.reps
  )
}

/**
 * The band for a set not yet done, at the rep count the program is asking for.
 *
 * Where a history band is one rep count with uncertainty either side, this one is the envelope
 * over the whole prescribed range: [upper] is the weight you would be over the curve at if you
 * only managed [targetReps], [lower] the weight you would be under it at even taking the full
 * [targetRepsHigh]. Anything landing between them is on the curve for *some* rep count you were
 * asked for, which is the actual question - "what do I load next?" - and it collapses back to an
 * ordinary band when the program prescribes a single number.
 *
 * Evaluated at the target rather than at whatever reps the last set happened to take: a history
 * skewed to heavy triples would otherwise recommend a weight nobody can get ten reps with.
 */
fun ScoredSet.forwardBandAt(x: Float, targetReps: Int, targetRepsHigh: Int): EffortBand? {
  val heavy = expectedWeightAt(targetReps) ?: return null
  val light = expectedWeightAt(targetRepsHigh) ?: return null
  val heavyHalf = bandHalfWidthAt(targetReps) ?: return null
  val lightHalf = bandHalfWidthAt(targetRepsHigh) ?: return null
  return EffortBand(
    x = x,
    lower = (light + EffortModel.UNDER_EDGE_Z * lightHalf).toFloat(),
    center = heavy.toFloat(),
    upper = (heavy + EffortModel.OVER_EDGE_Z * heavyHalf).toFloat(),
    reps = targetReps
  )
}

/**
 * Breaks the expectation band into runs - a set with no expectation (cold start, or belonging
 * to the other [ExpectationSource]) breaks the ribbon rather than bridging across the gap, so a
 * caller can draw the real fit and its coarser stand-in as visually distinct bands.
 *
 * Order matters: runs are built in list order, so callers must already be sorted along x.
 */
fun List<ScoredSet>.bandRuns(
  source: ExpectationSource,
  x: (index: Int, set: ScoredSet) -> Float = { index, _ -> index.toFloat() }
): List<List<EffortBand>> {
  val runs = mutableListOf<MutableList<EffortBand>>()
  var current: MutableList<EffortBand>? = null
  forEachIndexed { index, scored ->
    val band = if (scored.expectationSource == source) scored.bandAt(x(index, scored)) else null
    if (band == null) {
      current = null
    } else {
      val run = current ?: mutableListOf<EffortBand>().also {
        current = it
        runs.add(it)
      }
      run.add(band)
    }
  }
  return runs
}
