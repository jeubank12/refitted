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

/**
 * One per-set band/trend value, tagged with which session it belongs to and whether the model
 * has already collapsed it to one number for that whole session (`ExpectationSource.SESSION`)
 * rather than letting it vary set to set (`BOOTSTRAP`, including the always-live current
 * session - see `EffortModel.scoreWithBootstrap`).
 */
data class BandPoint(val x: Float, val y: Double?, val sessionIndex: Int, val collapsed: Boolean)

/**
 * Reduces a run of [BandPoint]s belonging to the same [BandPoint.collapsed] session to the one
 * logical point [buildTrendRuns] should connect a line through, instead of plotting one point
 * per set and redrawing the same flat segment [buildTrendRuns] would otherwise walk across N
 * times. The collapsed value is identical across the whole session already (that's what
 * collapsed means), so which of its sets supplies it doesn't matter; the x lands at the
 * session's own midpoint so the connecting line meets it where the session visually sits rather
 * than at whichever set happened to be first or last in the window.
 *
 * A point that isn't collapsed (BOOTSTRAP/live, genuinely per-set) passes through unchanged, one
 * result entry per input point - this is a no-op on an all-uncollapsed input. A null [BandPoint.y]
 * also passes through unchanged, still breaking [buildTrendRuns]' polyline at that spot.
 */
fun collapseSessions(points: List<BandPoint>): List<Pair<Float, Double?>> {
  val result = mutableListOf<Pair<Float, Double?>>()
  var i = 0
  while (i < points.size) {
    val point = points[i]
    if (point.collapsed && point.y != null) {
      var j = i
      while (j + 1 < points.size &&
        points[j + 1].sessionIndex == point.sessionIndex &&
        points[j + 1].collapsed
      ) {
        j++
      }
      result.add((points[i].x + points[j].x) / 2f to point.y)
      i = j + 1
    } else {
      result.add(point.x to point.y)
      i++
    }
  }
  return result
}
