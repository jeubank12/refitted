package com.litus_animae.refitted.ui.compose.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.litus_animae.refitted.data.effort.EffortZone
import com.litus_animae.refitted.ui.compose.util.Theme
import kotlin.math.sqrt

/** One bubble to draw: [x] and [weight] in the caller's chosen domain, [size] in `[0, 1]`. */
data class EffortPoint(val x: Float, val weight: Float, val size: Float, val zone: EffortZone)

/**
 * Effort-scored sets as bubbles (radius by demonstrated capacity vs. expectation) plus the
 * adaptive expectation curve. See `docs/exercise-history-chart.md`.
 *
 * [trend] is a list of runs rather than one flat polyline - a run breaks wherever the caller's
 * domain has no prediction (cold start, or a skipped index in a compact window), and each run
 * is drawn as its own connected segment rather than one line bridging the gap.
 */
@Composable
fun EffortChart(
  modifier: Modifier = Modifier,
  points: List<EffortPoint>,
  trend: List<List<Pair<Float, Float>>> = emptyList(),
  compact: Boolean = false,
  baseColor: Color = MaterialTheme.colors.primary,
  peakColor: Color = Theme.goodAttention,
  punishedColor: Color = Theme.timerAmber,
  coldColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.25f),
  trendColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
  minPointSize: Dp = if (compact) 4.dp else 8.dp,
  maxPointSize: Dp = if (compact) 16.dp else 30.dp,
  trendWidth: Dp = if (compact) 1.5.dp else 2.dp
) {
  // rangeOf (used by the other charts) throws on empty input, and those charts only survive
  // because their caller happens to gate on itemCount > 0 - guard here instead of relying on it.
  if (points.isEmpty()) {
    Spacer(modifier)
    return
  }

  val minX = remember(points, trend) { minOf(points.minOf { it.x }, trend.flatten().minOfOrNull { it.first } ?: Float.POSITIVE_INFINITY) }
  val maxX = remember(points, trend) { maxOf(points.maxOf { it.x }, trend.flatten().maxOfOrNull { it.first } ?: Float.NEGATIVE_INFINITY) }
  val minY = remember(points, trend) { minOf(points.minOf { it.weight }, trend.flatten().minOfOrNull { it.second } ?: Float.POSITIVE_INFINITY) }
  val maxY = remember(points, trend) { maxOf(points.maxOf { it.weight }, trend.flatten().maxOfOrNull { it.second } ?: Float.NEGATIVE_INFINITY) }

  val xSpan = maxX - minX
  val ySpan = maxY - minY

  // A collapsed domain (single point, or every value identical) has no meaningful position -
  // center it rather than dividing by the max(range, 1f) floor the other charts use, which
  // would silently pin everything to one edge instead.
  fun nx(x: Float) = if (xSpan <= 0f) 0.5f else (x - minX) / xSpan
  fun ny(y: Float) = if (ySpan <= 0f) 0.5f else (y - minY) / ySpan

  val minPx = with(LocalDensity.current) { minPointSize.toPx() }
  val maxPx = with(LocalDensity.current) { maxPointSize.toPx() }
  val trendPx = with(LocalDensity.current) { trendWidth.toPx() }

  val canvasModifier = if (compact) {
    modifier.padding(horizontal = 6.dp, vertical = 4.dp)
  } else {
    modifier.padding(20.dp).defaultMinSize(100.dp, 100.dp)
  }

  Canvas(canvasModifier) {
    trend.forEach { run ->
      if (run.size < 2) return@forEach
      val offsets = run.map { (x, y) -> Offset(lerp(0f, size.width, nx(x)), lerp(size.height, 0f, ny(y))) }
      drawPoints(
        offsets.zipWithNext().flatMap { sequenceOf(it.first, it.second) },
        PointMode.Lines,
        trendColor,
        trendPx
      )
    }

    // Largest first so a peak bubble never visually swallows a smaller one drawn after it.
    points.sortedByDescending { it.size }.forEach { point ->
      // Stroke width is a diameter, so mapping size -> diameter linearly would make the
      // *area* (what the eye actually reads as "bigger") grow quadratically with size.
      // Interpolating the squared diameter and taking the root keeps size proportional to area.
      val clampedSize = point.size.coerceIn(0f, 1f)
      val diameter = sqrt(lerp(minPx * minPx, maxPx * maxPx, clampedSize))
      val color = zoneColor(point.zone, baseColor, peakColor, punishedColor, coldColor)
      drawPoints(
        listOf(Offset(lerp(0f, size.width, nx(point.x)), lerp(size.height, 0f, ny(point.weight)))),
        PointMode.Points,
        color,
        diameter,
        StrokeCap.Round
      )
    }
  }
}

private fun zoneColor(
  zone: EffortZone,
  baseColor: Color,
  peakColor: Color,
  punishedColor: Color,
  coldColor: Color
): Color = when (zone) {
  EffortZone.COLD -> coldColor
  EffortZone.BELOW -> baseColor.copy(alpha = 0.55f)
  EffortZone.ON_CURVE -> baseColor.copy(alpha = 0.85f)
  EffortZone.GROWTH -> peakColor
  EffortZone.IMPLAUSIBLE -> punishedColor
}

@Preview
@Composable
private fun PreviewEffortChartEmpty() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = emptyList()
  )
}

@Preview
@Composable
private fun PreviewEffortChartSinglePoint() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = listOf(EffortPoint(0f, 100f, 0.45f, EffortZone.COLD))
  )
}

@Preview
@Composable
private fun PreviewEffortChartTwoSameDay() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = listOf(
      EffortPoint(0f, 95f, 0.45f, EffortZone.COLD),
      EffortPoint(0f, 100f, 0.45f, EffortZone.COLD)
    )
  )
}

@Preview
@Composable
private fun PreviewEffortChartColdStartOnly() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = listOf(
      EffortPoint(0f, 90f, 0.45f, EffortZone.COLD),
      EffortPoint(1f, 95f, 0.45f, EffortZone.COLD)
    )
  )
}

@Preview
@Composable
private fun PreviewEffortChartLongHistoryWithSpike() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = listOf(
      EffortPoint(0f, 90f, 0.45f, EffortZone.COLD),
      EffortPoint(1f, 92f, 0.45f, EffortZone.COLD),
      EffortPoint(2f, 95f, 0.45f, EffortZone.COLD),
      EffortPoint(3f, 98f, 0.70f, EffortZone.ON_CURVE),
      EffortPoint(4f, 100f, 0.85f, EffortZone.GROWTH),
      EffortPoint(5f, 130f, 0.40f, EffortZone.IMPLAUSIBLE),
      EffortPoint(6f, 103f, 0.60f, EffortZone.ON_CURVE),
      EffortPoint(7f, 105f, 1.00f, EffortZone.GROWTH)
    ),
    trend = listOf(
      listOf(3f to 96f, 4f to 98f, 5f to 100f, 6f to 101f, 7f to 103f)
    )
  )
}

@Preview
@Composable
private fun PreviewEffortChartFlatHistory() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = (0..6).map { EffortPoint(it.toFloat(), 100f, 0.60f, EffortZone.ON_CURVE) },
    trend = listOf((3..6).map { it.toFloat() to 100f })
  )
}
