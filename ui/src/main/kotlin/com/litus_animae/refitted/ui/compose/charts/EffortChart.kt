package com.litus_animae.refitted.ui.compose.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.litus_animae.refitted.data.effort.EffortZone
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.util.ExtendedTheme
import com.litus_animae.refitted.ui.compose.util.RefittedTheme
import kotlin.math.sqrt

/**
 * One bubble to draw: [x] and [weight] in the caller's chosen domain, [size] in `[0, 1]`.
 * [emphasized] draws a ring around this bubble (e.g. "the set you just did") - false costs
 * nothing extra to draw.
 */
data class EffortPoint(
  val x: Float,
  val weight: Float,
  val size: Float,
  val zone: EffortZone,
  val emphasized: Boolean = false
)

private val LabelPadding = 4.dp
private val GapMarkDash = 4.dp
private val EmphasisRingGap = 3.dp
private val EmphasisRingWidth = 1.5.dp

/**
 * Effort-scored sets as bubbles (radius by rep count, color by demonstrated capacity vs.
 * expectation) plus the adaptive expectation curve.
 *
 * [trend] is a list of runs rather than one flat polyline - a run breaks wherever the caller's
 * domain has no prediction (cold start, or a skipped index in a compact window), and each run
 * is drawn as its own connected segment rather than one line bridging the gap.
 *
 * [xLabels] and [yLabels] are axis ticks in the caller's domain (`domain value to display
 * text`); [gapMarks] are x-domain positions for a dashed rule (e.g. between sessions with a
 * long calendar gap between them). All three default empty and cost nothing when unused - the
 * compact strip never passes them.
 */
@Composable
fun EffortChart(
  modifier: Modifier = Modifier,
  points: List<EffortPoint>,
  trend: List<List<Pair<Float, Float>>> = emptyList(),
  // A second trend line, drawn dashed and beneath [trend] - for callers that fit two kinds
  // of expectation (e.g. a coarser stand-in while there isn't enough history for the real
  // one) and want the dash itself to carry that distinction rather than a text label.
  dashedTrend: List<List<Pair<Float, Float>>> = emptyList(),
  // The funnel background: [bandTop] and [bandBottom] are drawn beneath everything else as a
  // filled ribbon per matching run pair (straight lines between points, no smoothing yet).
  // Callers build both with the same run-breaking helper (`buildTrendRuns`) they already use
  // for [trend], so a run's index/length always lines up between the two lists.
  bandTop: List<List<Pair<Float, Float>>> = emptyList(),
  bandBottom: List<List<Pair<Float, Float>>> = emptyList(),
  compact: Boolean = false,
  xLabels: List<Pair<Float, String>> = emptyList(),
  yLabels: List<Pair<Float, String>> = emptyList(),
  gapMarks: List<Float> = emptyList(),
  baseColor: Color = MaterialTheme.colorScheme.primary,
  peakColor: Color = ExtendedTheme.colors.goodAttention.color,
  punishedColor: Color = ExtendedTheme.colors.timerAmber.color,
  coldColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
  trendColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
  emphasisColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
  funnelColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
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

  val minX = remember(points, trend, dashedTrend, bandTop, bandBottom) {
    minOf(
      points.minOf { it.x },
      (trend + dashedTrend + bandTop + bandBottom).flatten().minOfOrNull { it.first } ?: Float.POSITIVE_INFINITY
    )
  }
  val maxX = remember(points, trend, dashedTrend, bandTop, bandBottom) {
    maxOf(
      points.maxOf { it.x },
      (trend + dashedTrend + bandTop + bandBottom).flatten().maxOfOrNull { it.first } ?: Float.NEGATIVE_INFINITY
    )
  }
  val minY = remember(points, trend, dashedTrend, bandTop, bandBottom) {
    minOf(
      points.minOf { it.weight },
      (trend + dashedTrend + bandTop + bandBottom).flatten().minOfOrNull { it.second } ?: Float.POSITIVE_INFINITY
    )
  }
  val maxY = remember(points, trend, dashedTrend, bandTop, bandBottom) {
    maxOf(
      points.maxOf { it.weight },
      (trend + dashedTrend + bandTop + bandBottom).flatten().maxOfOrNull { it.second } ?: Float.NEGATIVE_INFINITY
    )
  }

  val xSpan = maxX - minX
  val ySpan = maxY - minY

  // A collapsed domain (single point, or every value identical) has no meaningful position -
  // center it rather than dividing by the max(range, 1f) floor the other charts use, which
  // would silently pin everything to one edge instead.
  fun nx(x: Float) = if (xSpan <= 0f) 0.5f else (x - minX) / xSpan
  fun ny(y: Float) = if (ySpan <= 0f) 0.5f else (y - minY) / ySpan

  // Largest first so a peak bubble never visually swallows a smaller one drawn after it -
  // hoisted out of the draw scope so it isn't re-sorted every frame.
  val sortedPoints = remember(points) { points.sortedByDescending { it.size } }

  val density = LocalDensity.current
  val minPx = with(density) { minPointSize.toPx() }
  val maxPx = with(density) { maxPointSize.toPx() }
  val trendPx = with(density) { trendWidth.toPx() }
  val labelPaddingPx = with(density) { LabelPadding.toPx() }
  val gapDashPx = with(density) { GapMarkDash.toPx() }
  val emphasisGapPx = with(density) { EmphasisRingGap.toPx() }
  val emphasisWidthPx = with(density) { EmphasisRingWidth.toPx() }

  val textMeasurer = rememberTextMeasurer()
  val labelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
  val xLabelLayouts = remember(xLabels, labelStyle) {
    xLabels.map { (x, text) -> x to textMeasurer.measure(text, labelStyle) }
  }
  val yLabelLayouts = remember(yLabels, labelStyle) {
    yLabels.map { (y, text) -> y to textMeasurer.measure(text, labelStyle) }
  }
  val leftGutter = if (yLabelLayouts.isEmpty()) 0f else {
    yLabelLayouts.maxOf { it.second.size.width } + labelPaddingPx
  }
  val bottomGutter = if (xLabelLayouts.isEmpty()) 0f else {
    xLabelLayouts.maxOf { it.second.size.height } + labelPaddingPx
  }

  val canvasModifier = if (compact) {
    modifier.padding(horizontal = 6.dp, vertical = 4.dp)
  } else {
    modifier
      .padding(8.dp)
      .defaultMinSize(100.dp, 100.dp)
  }

  Canvas(canvasModifier) {
    // Bubble centers are inset by their own radius so the largest bubble's edge lands on the
    // canvas boundary instead of its center - previously only the composable's outer padding
    // stood between a max-size edge bubble and clipping by whatever container (e.g. a Card)
    // sits around this chart.
    val maxR = maxPx / 2f
    val plotLeft = (maxR + leftGutter).coerceAtMost(size.width / 2f)
    val plotRight = (size.width - maxR).coerceAtLeast(plotLeft)
    val plotTop = maxR.coerceAtMost(size.height / 2f)
    val plotBottom = (size.height - maxR - bottomGutter).coerceAtLeast(plotTop)

    fun px(x: Float) = lerp(plotLeft, plotRight, nx(x))
    fun py(y: Float) = lerp(plotBottom, plotTop, ny(y))

    // Drawn first and beneath everything else - the funnel is a backdrop, not a data series in
    // its own right. Runs are paired by index with bandBottom, matching how the caller built
    // both from the same underlying points via buildTrendRuns.
    bandTop.zip(bandBottom).forEach { (top, bottom) ->
      if (top.size < 2 || bottom.size < 2) return@forEach
      val path = Path().apply {
        top.forEachIndexed { index, (x, y) ->
          val offset = Offset(px(x), py(y))
          if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
        }
        bottom.asReversed().forEach { (x, y) -> lineTo(px(x), py(y)) }
        close()
      }
      drawPath(path, funnelColor)
    }

    gapMarks.forEach { x ->
      drawLine(
        coldColor,
        Offset(px(x), plotTop),
        Offset(px(x), plotBottom),
        strokeWidth = trendPx / 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(gapDashPx, gapDashPx))
      )
    }

    // Drawn first, and beneath the solid trend below, so a solid segment starting exactly
    // where a dashed one ends (the bootstrap-to-real handoff) renders over it cleanly.
    dashedTrend.forEach { run ->
      if (run.size < 2) return@forEach
      val path = Path().apply {
        run.forEachIndexed { index, (x, y) ->
          val offset = Offset(px(x), py(y))
          if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
        }
      }
      drawPath(
        path,
        trendColor,
        style = Stroke(width = trendPx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(gapDashPx, gapDashPx)))
      )
    }

    trend.forEach { run ->
      if (run.size < 2) return@forEach
      val offsets = run.map { (x, y) -> Offset(px(x), py(y)) }
      drawPoints(
        offsets.zipWithNext().flatMap { sequenceOf(it.first, it.second) },
        PointMode.Lines,
        trendColor,
        trendPx
      )
    }

    sortedPoints.forEach { point ->
      // Stroke width is a diameter, so mapping size -> diameter linearly would make the
      // *area* (what the eye actually reads as "bigger") grow quadratically with size.
      // Interpolating the squared diameter and taking the root keeps size proportional to area.
      val clampedSize = point.size.coerceIn(0f, 1f)
      val diameter = sqrt(lerp(minPx * minPx, maxPx * maxPx, clampedSize))
      val color = zoneColor(point.zone, baseColor, peakColor, punishedColor, coldColor)
      val center = Offset(px(point.x), py(point.weight))
      drawPoints(listOf(center), PointMode.Points, color, diameter, StrokeCap.Round)
      if (point.emphasized) {
        drawCircle(
          emphasisColor,
          radius = diameter / 2f + emphasisGapPx,
          center = center,
          style = Stroke(width = emphasisWidthPx)
        )
      }
    }

    yLabelLayouts.forEach { (y, layout) ->
      drawText(
        layout,
        topLeft = Offset(plotLeft - maxR - labelPaddingPx - layout.size.width, py(y) - layout.size.height / 2f)
      )
    }
    xLabelLayouts.forEach { (x, layout) ->
      val left = (px(x) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
      drawText(layout, topLeft = Offset(left, plotBottom + maxR + labelPaddingPx))
    }
  }
}

/** Dot + label per [EffortZone], decoding the bubble colors drawn by [EffortChart]. */
@Composable
fun EffortLegend(
  modifier: Modifier = Modifier,
  baseColor: Color = MaterialTheme.colorScheme.primary,
  peakColor: Color = ExtendedTheme.colors.goodAttention.color,
  punishedColor: Color = ExtendedTheme.colors.timerAmber.color,
  coldColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
) {
  FlowRow(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    EffortZone.entries.forEach { zone ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
          Modifier
            .size(8.dp)
            .background(
              zoneColor(zone, baseColor, peakColor, punishedColor, coldColor),
              CircleShape
            )
        )
        Text(stringResource(zoneLabelRes(zone)), style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

internal fun zoneLabelRes(zone: EffortZone): Int = when (zone) {
  EffortZone.COLD -> R.string.effort_zone_cold
  EffortZone.BELOW -> R.string.effort_zone_below
  EffortZone.ON_CURVE -> R.string.effort_zone_on_curve
  EffortZone.GROWTH -> R.string.effort_zone_growth
  EffortZone.IMPLAUSIBLE -> R.string.effort_zone_implausible
}

internal fun zoneColor(
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

private val darkTheme = false

@Preview
@Composable
private fun PreviewEffortChartEmpty() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = emptyList()
    )
  }
}

@Preview
@Composable
private fun PreviewEffortChartSinglePoint() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = listOf(EffortPoint(0f, 100f, 0.45f, EffortZone.COLD))
    )
  }
}

@Preview
@Composable
private fun PreviewEffortChartTwoSameDay() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = listOf(
        EffortPoint(0f, 95f, 0.45f, EffortZone.COLD),
        EffortPoint(0f, 100f, 0.45f, EffortZone.COLD)
      )
    )
  }
}

@Preview
@Composable
private fun PreviewEffortChartColdStartOnly() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = listOf(
        EffortPoint(0f, 90f, 0.45f, EffortZone.COLD),
        EffortPoint(1f, 95f, 0.45f, EffortZone.COLD)
      )
    )
  }
}

@Preview
@Composable
private fun PreviewEffortChartLongHistoryWithSpike() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
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
}

@Preview
@Composable
private fun PreviewEffortChartEmphasizedNewest() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = listOf(
        EffortPoint(0f, 90f, 0.45f, EffortZone.COLD),
        EffortPoint(1f, 95f, 0.60f, EffortZone.ON_CURVE),
        EffortPoint(2f, 98f, 0.85f, EffortZone.GROWTH, emphasized = true)
      ),
      gapMarks = listOf(0.5f, 1.5f),
      yLabels = listOf(90f to "90", 98f to "98")
    )
  }
}

@Preview
@Composable
private fun PreviewEffortChartDashedTrendHandoff() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = listOf(
        EffortPoint(0f, 90f, 0.45f, EffortZone.COLD),
        EffortPoint(1f, 95f, 0.60f, EffortZone.ON_CURVE),
        EffortPoint(2f, 98f, 0.60f, EffortZone.ON_CURVE),
        EffortPoint(3f, 100f, 0.70f, EffortZone.ON_CURVE),
        EffortPoint(4f, 103f, 0.85f, EffortZone.GROWTH)
      ),
      // The bootstrap (dashed) run hands off to the real (solid) run at x=2 - the solid
      // segment should render cleanly over the dash where they overlap.
      dashedTrend = listOf(listOf(1f to 94f, 2f to 97f)),
      trend = listOf(listOf(2f to 97f, 3f to 99f, 4f to 101f))
    )
  }
}

@Preview
@Composable
private fun PreviewEffortChartFlatHistory() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = (0..6).map { EffortPoint(it.toFloat(), 100f, 0.60f, EffortZone.ON_CURVE) },
      trend = listOf((3..6).map { it.toFloat() to 100f })
    )
  }
}

/** Regression check for the plot-rect inset: max-size bubbles sit at every domain corner and
 * must not touch the [Color.White] box edge. */
@Preview
@Composable
private fun PreviewEffortChartCornerPoints() {
  RefittedTheme(darkTheme = darkTheme) {
  EffortChart(
    Modifier
      .size(300.dp)
      .background(MaterialTheme.colorScheme.surfaceContainer),
    points = listOf(
      EffortPoint(0f, 0f, 1.00f, EffortZone.GROWTH),
      EffortPoint(0f, 100f, 1.00f, EffortZone.GROWTH),
      EffortPoint(10f, 0f, 1.00f, EffortZone.GROWTH),
      EffortPoint(10f, 100f, 1.00f, EffortZone.GROWTH)
    )
  )
  }
}

@Preview
@Composable
private fun PreviewEffortChartWithAxesAndGaps() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(340.dp, 220.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = listOf(
        EffortPoint(0f, 20f, 0.30f, EffortZone.COLD),
        EffortPoint(1f, 20f, 0.30f, EffortZone.COLD),
        EffortPoint(2f, 15f, 0.60f, EffortZone.ON_CURVE),
        EffortPoint(3f, 35f, 0.85f, EffortZone.GROWTH),
        EffortPoint(4f, 40f, 1.00f, EffortZone.GROWTH)
      ),
      trend = listOf(listOf(3f to 33f, 4f to 38f)),
      gapMarks = listOf(1.5f, 2.5f),
      xLabels = listOf(0f to "Nov '21", 2f to "Jan '23", 4f to "Jun '23"),
      yLabels = listOf(15f to "15", 40f to "40")
    )
  }
}

@Preview
@Composable
private fun PreviewEffortChartFunnelBand() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = listOf(
        EffortPoint(0f, 90f, 0.45f, EffortZone.COLD),
        EffortPoint(1f, 92f, 0.45f, EffortZone.COLD),
        EffortPoint(2f, 95f, 0.45f, EffortZone.COLD),
        EffortPoint(3f, 98f, 0.70f, EffortZone.ON_CURVE),
        EffortPoint(4f, 100f, 0.85f, EffortZone.GROWTH),
        EffortPoint(5f, 103f, 0.60f, EffortZone.ON_CURVE)
      ),
      trend = listOf(listOf(3f to 96f, 4f to 98f, 5f to 100f)),
      bandTop = listOf(listOf(3f to 110f, 4f to 113f, 5f to 116f)),
      bandBottom = listOf(listOf(3f to 82f, 4f to 84f, 5f to 86f))
    )
  }
}

@Preview
@Composable
private fun PreviewEffortChartFunnelBandWithGap() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = listOf(
        EffortPoint(0f, 90f, 0.45f, EffortZone.COLD),
        EffortPoint(1f, 95f, 0.60f, EffortZone.ON_CURVE),
        EffortPoint(2f, 98f, 0.60f, EffortZone.ON_CURVE),
        EffortPoint(3f, 105f, 0.85f, EffortZone.GROWTH),
        EffortPoint(4f, 108f, 0.85f, EffortZone.GROWTH)
      ),
      gapMarks = listOf(1.5f),
      // Two disjoint bands, matching how a mid-history gap already breaks the trend line.
      bandTop = listOf(listOf(0f to 100f, 1f to 104f), listOf(2f to 112f, 3f to 116f, 4f to 119f)),
      bandBottom = listOf(listOf(0f to 80f, 1f to 84f), listOf(2f to 88f, 3f to 92f, 4f to 95f))
    )
  }
}

@Preview
@Composable
private fun PreviewEffortLegend() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortLegend(Modifier
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .padding(8.dp))
  }
}
