package com.litus_animae.refitted.ui.compose.charts

import com.litus_animae.refitted.data.effort.EffortModel
import com.litus_animae.refitted.data.effort.ExpectationSource
import com.litus_animae.refitted.data.effort.ScoredSet

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
