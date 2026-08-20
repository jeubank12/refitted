package com.litus_animae.refitted.ui.compose.charts

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
 *
 * [zone] alone only buckets a set into 5 flat colors; [z] is the raw score behind it, so the
 * dot can be colored continuously - how far above/below expectation, not just which bucket -
 * matching the funnel band's own gradient instead of stepping between flat zone colors. `null`
 * (no expectation yet, i.e. [EffortZone.COLD]) falls back to a flat [zone] color.
 */
data class EffortPoint(
  val x: Float,
  val weight: Float,
  val size: Float,
  val zone: EffortZone,
  val z: Double? = null,
  val emphasized: Boolean = false
)

private val LabelPadding = 4.dp
private val GapMarkDash = 4.dp
private val EmphasisRingGap = 3.dp
private val EmphasisRingWidth = 1.5.dp
private val BandFadeInset = 4.dp
private val FadeOutlineWidth = 1.dp

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
  // The trend/expectation weight at each of [bandTop]/[bandBottom]'s x-samples - the same
  // values [trend] draws, reused here only to position the gradient's midline color stop
  // within the band. Must share [bandTop]/[bandBottom]'s run structure (same x-samples, same
  // run breaks); a segment with no matching bandMid run falls back to a flat [funnelColor].
  bandMid: List<List<Pair<Float, Float>>> = emptyList(),
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
  // How opaque the gradient fill is - the flat backdrop this replaced was a wash at 8% (see
  // [funnelColor]); a 4-color gradient needs more presence than that to read at all, but should
  // still sit behind the trend line, dots, and gridlines rather than fighting them for contrast.
  bandAlpha: Float = 0.45f,
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

  val minX = remember(points, trend, dashedTrend, bandTop, bandBottom, bandMid) {
    minOf(
      points.minOf { it.x },
      (trend + dashedTrend + bandTop + bandBottom + bandMid).flatten().minOfOrNull { it.first }
        ?: Float.POSITIVE_INFINITY
    )
  }
  val maxX = remember(points, trend, dashedTrend, bandTop, bandBottom, bandMid) {
    maxOf(
      points.maxOf { it.x },
      (trend + dashedTrend + bandTop + bandBottom + bandMid).flatten().maxOfOrNull { it.first }
        ?: Float.NEGATIVE_INFINITY
    )
  }
  val minY = remember(points, trend, dashedTrend, bandTop, bandBottom, bandMid) {
    minOf(
      points.minOf { it.weight },
      (trend + dashedTrend + bandTop + bandBottom + bandMid).flatten().minOfOrNull { it.second }
        ?: Float.POSITIVE_INFINITY
    )
  }
  val maxY = remember(points, trend, dashedTrend, bandTop, bandBottom, bandMid) {
    maxOf(
      points.maxOf { it.weight },
      (trend + dashedTrend + bandTop + bandBottom + bandMid).flatten().maxOfOrNull { it.second }
        ?: Float.NEGATIVE_INFINITY
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
  val bandFadePx = with(density) { BandFadeInset.toPx() }
  val fadeOutlinePx = with(density) { FadeOutlineWidth.toPx() }

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

  val meshPaint = remember { NativePaint() }

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
    // its own right. Runs are paired by index with bandBottom (and bandMid, when present),
    // matching how the caller built all three from the same underlying points via
    // buildTrendRuns. A `Brush` can only vary color along one straight screen-space axis, which
    // can't follow a band that both tilts (weight rising/falling across x) and needs its
    // midline stop positioned per-x - so this is a triangle mesh instead, with each x-sample
    // contributing one shared column of 4 vertices (cold/on-curve/growth/overreach) that
    // neighboring segments both reference, keeping colors continuous across segment boundaries
    // instead of restarting per segment.
    bandTop.zip(bandBottom).forEachIndexed { runIndex, (top, bottom) ->
      if (top.size < 2 || bottom.size < 2) return@forEachIndexed
      val mid = bandMid.getOrNull(runIndex)
      if (mid == null || mid.size != top.size) {
        val path = Path().apply {
          top.forEachIndexed { index, (x, y) ->
            val offset = Offset(px(x), py(y))
            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
          }
          bottom.asReversed().forEach { (x, y) -> lineTo(px(x), py(y)) }
          close()
        }
        drawPath(path, funnelColor)
        return@forEachIndexed
      }

      val n = top.size
      // 5 rows per column. Below the on-curve stop there's no separate "cold" color any more -
      // the on-curve blue itself just fades out (alpha only, same RGB) all the way down to the
      // band's bottom edge, so "under expectation" reads as this color thinning out rather than
      // a second hue taking over. The top still fades outward a few px *beyond* its edge (see
      // [BandFadeInset]) since a hard color->color edge there read fine, unlike the bottom.
      val verts = FloatArray(n * 5 * 2)
      val colors = IntArray(n * 5)
      for (j in 0 until n) {
        val (x, topWeight) = top[j]
        val bottomWeight = bottom[j].second
        val midWeight = mid[j].second
        val span = topWeight - bottomWeight
        val fracTrend = if (span <= 0f) 0.5f else ((midWeight - bottomWeight) / span).coerceIn(0f, 1f)
        val fracGrowth = lerp(fracTrend, 1f, 0.5f)
        val xPx = px(x)
        val bottomPx = py(bottomWeight)
        val topPx = py(topWeight)
        val onCurvePx = lerp(bottomPx, topPx, fracTrend)
        val growthPx = lerp(bottomPx, topPx, fracGrowth)
        val baseColorArgb = baseColor.copy(alpha = bandAlpha).toArgb()
        val peakColorArgb = peakColor.copy(alpha = bandAlpha).toArgb()
        val punishedColorArgb = punishedColor.copy(alpha = bandAlpha).toArgb()
        // Ordered by increasing pixel y (screen-bottom to screen-top): the band's own bottom
        // edge (on-curve's color, faded to nothing), on-curve, growth, the real top edge,
        // beyond-top fade.
        val rowY = floatArrayOf(bottomPx, onCurvePx, growthPx, topPx, topPx - bandFadePx)
        val rowColors = intArrayOf(
          baseColorArgb and 0x00FFFFFF,
          baseColorArgb,
          peakColorArgb,
          punishedColorArgb,
          punishedColorArgb and 0x00FFFFFF
        )
        val base = j * 5
        for (row in rowY.indices) {
          verts[(base + row) * 2] = xPx
          verts[(base + row) * 2 + 1] = rowY[row]
          colors[base + row] = rowColors[row]
        }
      }
      // 2 triangles per grid cell (4 row-bands x (n - 1) column-gaps), listed explicitly rather
      // than as a single strip - a strip would need degenerate bridging triangles between runs,
      // and this chart already draws one run at a time.
      val indices = ShortArray((n - 1) * 4 * 6)
      var ii = 0
      for (j in 0 until n - 1) {
        for (row in 0 until 4) {
          val tl = (j * 5 + row).toShort()
          val bl = (j * 5 + row + 1).toShort()
          val tr = ((j + 1) * 5 + row).toShort()
          val br = ((j + 1) * 5 + row + 1).toShort()
          indices[ii++] = tl; indices[ii++] = bl; indices[ii++] = tr
          indices[ii++] = bl; indices[ii++] = br; indices[ii++] = tr
        }
      }
      drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawVertices(
          NativeCanvas.VertexMode.TRIANGLES,
          verts.size,
          verts,
          0,
          null,
          0,
          colors,
          0,
          indices,
          0,
          indices.size,
          meshPaint
        )
      }
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
      // Bubbles with a z-score read as solid, unlike the funnel band behind them - force full
      // opacity here rather than in effortColor itself, whose below-on-curve anchors carry real
      // white-blended RGB (not alpha) specifically so they stay visible once forced opaque. A
      // COLD dot (no expectation yet, z null) has no such RGB fade - it keeps zoneColor's true
      // alpha instead, since there's no band underneath it to fight for legibility against.
      val color = point.z
        ?.let { effortColor(it, baseColor, peakColor, punishedColor, coldColor).copy(alpha = 1f) }
        ?: zoneColor(point.zone, baseColor, peakColor, punishedColor, coldColor)
      val center = Offset(px(point.x), py(point.weight))
      drawPoints(listOf(center), PointMode.Points, color, diameter, StrokeCap.Round)
      val outlineFade = point.z?.let { effortOutlineFade(it) } ?: EffortColdOutlineFade
      if (outlineFade > 0f) {
        drawCircle(
          baseColor.copy(alpha = outlineFade * EffortOutlineMaxAlpha),
          radius = diameter / 2f,
          center = center,
          style = Stroke(width = fadeOutlinePx)
        )
      }
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

// Same z thresholds EffortModel.zoneOf buckets into zones (0.5, 2.0), plus a floor and ceiling a
// bit beyond either end so a dot deep in BELOW/IMPLAUSIBLE still reads as fully saturated rather
// than clamping right at the zone boundary.
//
// Bubbles with a real z-score are forced fully opaque (see the caller in EffortChart) so they
// stay legible over the funnel band - but zoneColor's BELOW/ON_CURVE only differ from baseColor
// by *alpha* (same RGB), so forcing opacity used to collapse COLD/BELOW/ON_CURVE into one
// indistinguishable flat color; nothing visibly changed until a dot reached peakColor or
// punishedColor's genuinely different hue. Below on-curve, this fades real RGB toward white
// instead of alpha (mirroring the funnel band's own "thins out toward the bottom edge" language,
// just via lightness instead of transparency since these dots can't go transparent), so the
// falloff below trend is visible immediately rather than only past an extreme threshold. Capped
// below 100% white (EffortColorFloorWhiteMix) so even the floor stays visibly tinted, not
// blending into a light background. One-sided: there's no analogous "too many reps" floor on the
// overreach side, so punishedColor stays a flat endpoint as before.
private const val EffortColorFloor = -2.0
private const val EffortColorCeiling = 3.0
private const val EffortColorFloorWhiteMix = 0.85f

// A near-white dot has no contrast against a light card on its own - the outline drawn around
// each bubble (see the caller in EffortChart) runs the fade in reverse: 0 alpha at/above
// on-curve, strengthening toward EffortOutlineMaxAlpha as the fill nears the white floor. COLD
// dots (no z yet) are always at that same muted starting point, so they always get the full
// outline rather than computing a fraction that has nothing to interpolate against.
private const val EffortOutlineMaxAlpha = 0.5f
private const val EffortColdOutlineFade = 1f

/** How much [effortColor] blended toward white at [z] - 1 at [EffortColorFloor], fading to 0 by
 * the on-curve boundary. Used to drive the fade-reversing outline, not the fill itself. */
private fun effortOutlineFade(z: Double): Float {
  val clamped = z.coerceIn(EffortColorFloor, 0.5)
  return 1f - ((clamped - EffortColorFloor) / (0.5 - EffortColorFloor)).toFloat()
}

/** Continuous version of [zoneColor] - where [z] falls between zone boundaries, not just which
 * zone it's in, so a dot's color shows how far off expectation it is. `null` has no z-based
 * color; callers fall back to [zoneColor] (COLD) instead. */
internal fun effortColor(
  z: Double,
  baseColor: Color,
  peakColor: Color,
  punishedColor: Color,
  coldColor: Color
): Color {
  val anchors = listOf(
    EffortColorFloor to lerp(coldColor, Color.White, EffortColorFloorWhiteMix),
    0.5 to baseColor,
    2.0 to peakColor,
    EffortColorCeiling to punishedColor
  )
  val clamped = z.coerceIn(EffortColorFloor, EffortColorCeiling)
  for (i in 0 until anchors.size - 1) {
    val (zLo, colorLo) = anchors[i]
    val (zHi, colorHi) = anchors[i + 1]
    if (clamped <= zHi) {
      val t = if (zHi <= zLo) 0f else ((clamped - zLo) / (zHi - zLo)).toFloat()
      return lerp(colorLo, colorHi, t)
    }
  }
  return punishedColor
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
      bandBottom = listOf(listOf(3f to 82f, 4f to 84f, 5f to 86f)),
      bandMid = listOf(listOf(3f to 96f, 4f to 98f, 5f to 100f))
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
      bandBottom = listOf(listOf(0f to 80f, 1f to 84f), listOf(2f to 88f, 3f to 92f, 4f to 95f)),
      bandMid = listOf(listOf(0f to 90f, 1f to 94f), listOf(2f to 100f, 3f to 104f, 4f to 107f))
    )
  }
}

/** Same band shape at three trend positions - near the bottom, centered, and near the top -
 * to tune the gradient's cold/on-curve/growth/overreach stops together. */
@Preview
@Composable
private fun PreviewEffortChartFunnelGradient() {
  RefittedTheme(darkTheme = darkTheme) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      listOf(
        "near bottom" to listOf(0f to 85f, 1f to 87f, 2f to 89f),
        "centered" to listOf(0f to 100f, 1f to 103f, 2f to 106f),
        "near top" to listOf(0f to 115f, 1f to 119f, 2f to 123f)
      ).forEach { (_, mid) ->
        EffortChart(
          Modifier
            .size(140.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
          points = listOf(
            EffortPoint(0f, 100f, 0.45f, EffortZone.ON_CURVE),
            EffortPoint(1f, 103f, 0.60f, EffortZone.ON_CURVE),
            EffortPoint(2f, 106f, 0.70f, EffortZone.GROWTH)
          ),
          bandTop = listOf(listOf(0f to 120f, 1f to 124f, 2f to 128f)),
          bandBottom = listOf(listOf(0f to 80f, 1f to 82f, 2f to 84f)),
          bandMid = listOf(mid)
        )
      }
    }
  }
}

/** A row of dots spanning cold through implausible, at a fixed weight so only [EffortPoint.z]
 * drives color - to tune [effortColor] against [zoneColor]'s legend swatches directly. */
@Preview
@Composable
private fun PreviewEffortChartContinuousDotColor() {
  RefittedTheme(darkTheme = darkTheme) {
    Column {
      EffortChart(
        Modifier
          .fillMaxWidth()
          .height(160.dp)
          .background(MaterialTheme.colorScheme.surfaceContainer),
        points = listOf(-2.5, -2.0, -1.5, -1.0, -0.75, -0.5, -0.25, 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.5)
          .mapIndexed { index, z -> EffortPoint(index.toFloat(), 100f, 0.6f, EffortZone.ON_CURVE, z = z) }
      )
      EffortLegend(Modifier.padding(8.dp))
    }
  }
}

@Preview
@Composable
private fun PreviewEffortChartFunnelGradientDark() {
  RefittedTheme(darkTheme = true) {
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
      bandBottom = listOf(listOf(3f to 82f, 4f to 84f, 5f to 86f)),
      bandMid = listOf(listOf(3f to 96f, 4f to 98f, 5f to 100f))
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
