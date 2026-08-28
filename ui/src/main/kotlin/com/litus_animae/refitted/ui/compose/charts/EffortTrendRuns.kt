package com.litus_animae.refitted.ui.compose.charts

/**
 * Breaks a trend line into runs keyed by contiguous session - a session with no predicted
 * weight (cold start, or filtered out of the caller's window) breaks the polyline rather
 * than bridging across the gap.
 *
 * [xBySessionIndex] is the x-domain position to draw each session at, paired with the
 * session index used to look it up in [expectedWeightBySession]. Order matters - runs are
 * built in list order, so callers must already be sorted along x.
 */
fun buildTrendRuns(
  xBySessionIndex: List<Pair<Float, Int>>,
  expectedWeightBySession: Map<Int, Double>
): List<List<Pair<Float, Float>>> {
  val runs = mutableListOf<MutableList<Pair<Float, Float>>>()
  var current: MutableList<Pair<Float, Float>>? = null
  xBySessionIndex.forEach { (x, sessionIndex) ->
    val expectedWeight = expectedWeightBySession[sessionIndex]
    if (expectedWeight == null) {
      current = null
    } else {
      val run = current ?: mutableListOf<Pair<Float, Float>>().also {
        current = it
        runs.add(it)
      }
      run.add(x to expectedWeight.toFloat())
    }
  }
  return runs
}

/**
 * Same run-breaking semantics as the session-keyed overload, for callers that already carry a
 * per-point expected weight and so don't need the session-index indirection. A null expectation
 * breaks the polyline; every other point connects directly to its neighbor.
 */
fun buildTrendRuns(points: List<Pair<Float, Double?>>): List<List<Pair<Float, Float>>> {
  val runs = mutableListOf<MutableList<Pair<Float, Float>>>()
  var current: MutableList<Pair<Float, Float>>? = null
  points.forEach { (x, expectedWeight) ->
    if (expectedWeight == null) {
      current = null
    } else {
      val run = current ?: mutableListOf<Pair<Float, Float>>().also {
        current = it
        runs.add(it)
      }
      run.add(x to expectedWeight.toFloat())
    }
  }
  return runs
}
