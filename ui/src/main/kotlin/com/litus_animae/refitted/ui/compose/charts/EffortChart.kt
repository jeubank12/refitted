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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import com.litus_animae.refitted.data.effort.EffortZone
import com.litus_animae.refitted.ui.R
import kotlin.math.sqrt

/**
 * One bubble to draw: [x] and [weight] in the caller's chosen domain. [emphasized] draws a ring
 * around this bubble (e.g. "the set you just did") - false costs nothing extra to draw.
 *
 * Size comes from [zone] in three discrete steps and colour from [reps]; neither is passed in,
 * so every chart drawing effort encodes them the same way. See [EffortSizeStep] and [RepRamp].
 */
data class EffortPoint(
  val x: Float,
  val weight: Float,
  val reps: Int,
  val zone: EffortZone,
  val emphasized: Boolean = false
)

/**
 * One x-position's slice of the expectation band: the weight the model expects at [x] and the
 * weights the zone thresholds sit at either side of it, all in the caller's y-domain.
 *
 * [lower] and [upper] are the BELOW/ON_CURVE and ON_CURVE/GROWTH boundaries expressed as
 * weights, so a bubble's vertical position against this band *is* its zone rather than
 * something the reader has to decode from its colour. [reps] tints the slice to match the dots
 * it belongs to.
 */
data class EffortBand(
  val x: Float,
  val lower: Float,
  val center: Float,
  val upper: Float,
  val reps: Int
)

/**
 * The three sizes a bubble can take. A continuum reads as noise here: the old one gave the
 * whole GROWTH zone about 1dp of diameter while spending 5dp inside ON_CURVE, so size varied
 * more within a zone than between them, and it was not even monotonic - one diameter meant
 * either "well under" or "implausibly over", leaving colour to say which.
 */
enum class EffortSizeStep { UNDER, ON, OVER }

internal fun sizeStepOf(zone: EffortZone): EffortSizeStep = when (zone) {
  EffortZone.BELOW -> EffortSizeStep.UNDER
  // A cold set has no expectation to be over or under, so it takes the neutral middle.
  EffortZone.COLD, EffortZone.ON_CURVE -> EffortSizeStep.ON
  // IMPLAUSIBLE is folded in rather than given a step of its own: with the band no longer
  // narrowing after a layoff it means a typo, and a typo is still an over-shoot.
  EffortZone.GROWTH, EffortZone.IMPLAUSIBLE -> EffortSizeStep.OVER
}

private fun EffortSizeStep.fraction(): Float = when (this) {
  EffortSizeStep.UNDER -> 0f
  EffortSizeStep.ON -> 0.5f
  EffortSizeStep.OVER -> 1f
}

/** The same three steps at swatch scale, for a legend key or an inline caption. */
internal fun sizeStepDot(step: EffortSizeStep): Dp = lerpDp(5.dp, 11.dp, step.fraction())

/**
 * Sequential single-hue ramp for rep count - light for few reps, dark for many.
 *
 * One hue, monotonically darkening, because reps are a magnitude: a rainbow would imply
 * categories that aren't there. It starts at Blue 500 rather than anything lighter because
 * every step has to clear 3:1 against this app's white chart surface, and Blue 400 only
 * manages 2.65:1; the range runs 3.12:1 to 8.63:1.
 */
private val RepRamp = listOf(
  Color(0xFF2196F3),
  Color(0xFF1E88E5),
  Color(0xFF1976D2),
  Color(0xFF1565C0),
  Color(0xFF0D47A1)
)

internal fun repColor(reps: Int, minReps: Int, maxReps: Int): Color {
  if (maxReps <= minReps) return RepRamp[RepRamp.size / 2]
  val t = ((reps - minReps).toFloat() / (maxReps - minReps)).coerceIn(0f, 1f)
  val pos = t * (RepRamp.size - 1)
  val index = pos.toInt().coerceAtMost(RepRamp.size - 2)
  return lerp(RepRamp[index], RepRamp[index + 1], pos - index)
}

private val LabelPadding = 4.dp
private val GapMarkDash = 4.dp
private val EmphasisRingGap = 3.dp
private val EmphasisRingWidth = 1.5.dp

/**
 * Effort-scored sets as bubbles against the band their trend expects them in.
 *
 * Three channels, one meaning each: vertical position is weight, size is where the set landed
 * against the band, colour is rep count. Nothing is encoded twice, and the zone a bubble is in
 * is legible from where it sits rather than from a colour the reader has to remember.
 *
 * [bands] is a list of runs rather than one flat ribbon - a run breaks wherever the caller's
 * domain has no prediction (cold start, or a skipped index in a compact window), and each run
 * is drawn as its own connected shape rather than one bridging the gap.
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
  bands: List<List<EffortBand>> = emptyList(),
  // A second band, drawn dashed - for callers that fit two kinds of expectation (e.g. a
  // coarser stand-in while there isn't enough history for the real one) and want the dash
  // itself to carry that distinction rather than a text label.
  dashedBands: List<List<EffortBand>> = emptyList(),
  compact: Boolean = false,
  xLabels: List<Pair<Float, String>> = emptyList(),
  yLabels: List<Pair<Float, String>> = emptyList(),
  gapMarks: List<Float> = emptyList(),
  coldColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.25f),
  trendColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
  emphasisColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
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

  val allBands = remember(bands, dashedBands) { (bands + dashedBands).flatten() }
  val minX = remember(points, allBands) {
    minOf(points.minOf { it.x }, allBands.minOfOrNull { it.x } ?: Float.POSITIVE_INFINITY)
  }
  val maxX = remember(points, allBands) {
    maxOf(points.maxOf { it.x }, allBands.maxOfOrNull { it.x } ?: Float.NEGATIVE_INFINITY)
  }
  val minY = remember(points, allBands) {
    minOf(points.minOf { it.weight }, allBands.minOfOrNull { it.lower } ?: Float.POSITIVE_INFINITY)
  }
  val maxY = remember(points, allBands) {
    maxOf(points.maxOf { it.weight }, allBands.maxOfOrNull { it.upper } ?: Float.NEGATIVE_INFINITY)
  }
  // The rep-count ramp is normalised across everything on screen, bands included, so a dot and
  // the slice of band it sits in read as the same colour when they describe the same reps.
  val minReps = remember(points, allBands) {
    minOf(points.minOf { it.reps }, allBands.minOfOrNull { it.reps } ?: Int.MAX_VALUE)
  }
  val maxReps = remember(points, allBands) {
    maxOf(points.maxOf { it.reps }, allBands.maxOfOrNull { it.reps } ?: Int.MIN_VALUE)
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
  val sortedPoints = remember(points) {
    points.sortedByDescending { sizeStepOf(it.zone).fraction() }
  }

  val density = LocalDensity.current
  val minPx = with(density) { minPointSize.toPx() }
  val maxPx = with(density) { maxPointSize.toPx() }
  val trendPx = with(density) { trendWidth.toPx() }
  val labelPaddingPx = with(density) { LabelPadding.toPx() }
  val gapDashPx = with(density) { GapMarkDash.toPx() }
  val emphasisGapPx = with(density) { EmphasisRingGap.toPx() }
  val emphasisWidthPx = with(density) { EmphasisRingWidth.toPx() }

  val textMeasurer = rememberTextMeasurer()
  val labelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
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
    modifier.padding(8.dp).defaultMinSize(100.dp, 100.dp)
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

    fun px(x: Float) = lerpFloat(plotLeft, plotRight, nx(x))
    fun py(y: Float) = lerpFloat(plotBottom, plotTop, ny(y))

    gapMarks.forEach { x ->
      drawLine(
        coldColor,
        Offset(px(x), plotTop),
        Offset(px(x), plotBottom),
        strokeWidth = trendPx / 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(gapDashPx, gapDashPx))
      )
    }

    // The band's own fill is what makes the thresholds readable: a bubble inside it is
    // ON_CURVE, above it is over, below it is under. Tinted along x by the reps each slice
    // describes, so the prediction is coloured on the same scale as the dots sitting in it.
    fun drawBand(run: List<EffortBand>, dashed: Boolean) {
      if (run.isEmpty()) return
      // A lone slice is the target for a set not done yet, so there is no ribbon to fill -
      // draw it as the upright bar it is, with a tick at the weight being aimed at.
      if (run.size == 1) {
        val b = run.single()
        val tint = repColor(b.reps, minReps, maxReps)
        val barWidth = maxPx / 3f
        drawLine(
          tint.copy(alpha = 0.35f),
          Offset(px(b.x), py(b.upper)),
          Offset(px(b.x), py(b.lower)),
          strokeWidth = barWidth,
          cap = StrokeCap.Round
        )
        drawLine(
          tint,
          Offset(px(b.x) - barWidth / 2f, py(b.center)),
          Offset(px(b.x) + barWidth / 2f, py(b.center)),
          strokeWidth = trendPx
        )
        return
      }
      if (run.size >= 2) {
        val area = Path().apply {
          run.forEachIndexed { index, b ->
            if (index == 0) moveTo(px(b.x), py(b.upper)) else lineTo(px(b.x), py(b.upper))
          }
          run.asReversed().forEach { b -> lineTo(px(b.x), py(b.lower)) }
          close()
        }
        val stops = run.mapIndexed { index, b ->
          (if (run.size == 1) 0f else index / (run.size - 1f)) to
            repColor(b.reps, minReps, maxReps).copy(alpha = if (dashed) 0.08f else 0.14f)
        }
        drawPath(
          area,
          Brush.horizontalGradient(
            colorStops = stops.toTypedArray(),
            startX = px(run.first().x),
            endX = px(run.last().x)
          )
        )
      }
      if (run.size < 2) return
      val centre = Path().apply {
        run.forEachIndexed { index, b ->
          if (index == 0) moveTo(px(b.x), py(b.center)) else lineTo(px(b.x), py(b.center))
        }
      }
      drawPath(
        centre,
        trendColor,
        style = Stroke(
          width = trendPx,
          pathEffect = if (dashed) {
            PathEffect.dashPathEffect(floatArrayOf(gapDashPx, gapDashPx))
          } else {
            null
          }
        )
      )
    }

    // Dashed first, so a solid run starting exactly where a dashed one ends (the
    // bootstrap-to-real handoff) renders over it cleanly.
    dashedBands.forEach { drawBand(it, dashed = true) }
    bands.forEach { drawBand(it, dashed = false) }

    sortedPoints.forEach { point ->
      // Stroke width is a diameter, so mapping size -> diameter linearly would make the
      // *area* (what the eye actually reads as "bigger") grow quadratically with size.
      // Interpolating the squared diameter and taking the root keeps size proportional to area.
      val diameter = sqrt(lerpFloat(minPx * minPx, maxPx * maxPx, sizeStepOf(point.zone).fraction()))
      val color = if (point.zone == EffortZone.COLD) {
        coldColor
      } else {
        repColor(point.reps, minReps, maxReps)
      }
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

/**
 * Decodes the two channels [EffortChart] actually uses: size for where a set landed against
 * the band, colour for how many reps it took. Zone is deliberately *not* listed colour-by-
 * colour any more - it is readable straight off the chart now, from whether a bubble sits
 * inside the band or outside it.
 */
@Composable
fun EffortLegend(
  modifier: Modifier = Modifier,
  repRange: IntRange? = null,
  coldColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.25f)
) {
  val inkColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
  FlowRow(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    EffortSizeStep.entries.forEach { step ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          Modifier
            .size(sizeStepDot(step))
            .background(inkColor, CircleShape)
        )
        Text(stringResource(sizeStepLabelRes(step)), style = MaterialTheme.typography.caption)
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Box(
        Modifier
          .size(8.dp)
          .background(coldColor, CircleShape)
      )
      Text(stringResource(R.string.effort_zone_cold), style = MaterialTheme.typography.caption)
    }
    if (repRange != null && repRange.last > repRange.first) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          stringResource(R.string.effort_reps_low, repRange.first),
          style = MaterialTheme.typography.caption
        )
        Box(
          Modifier
            .size(width = 36.dp, height = 8.dp)
            .background(
              Brush.horizontalGradient(
                listOf(
                  repColor(repRange.first, repRange.first, repRange.last),
                  repColor(repRange.last, repRange.first, repRange.last)
                )
              )
            )
        )
        Text(
          stringResource(R.string.effort_reps_high, repRange.last),
          style = MaterialTheme.typography.caption
        )
      }
    }
  }
}

internal fun sizeStepLabelRes(step: EffortSizeStep): Int = when (step) {
  EffortSizeStep.UNDER -> R.string.effort_step_under
  EffortSizeStep.ON -> R.string.effort_step_on
  EffortSizeStep.OVER -> R.string.effort_step_over
}

internal fun zoneLabelRes(zone: EffortZone): Int = when (zone) {
  EffortZone.COLD -> R.string.effort_zone_cold
  EffortZone.BELOW -> R.string.effort_zone_below
  EffortZone.ON_CURVE -> R.string.effort_zone_on_curve
  EffortZone.GROWTH -> R.string.effort_zone_growth
  EffortZone.IMPLAUSIBLE -> R.string.effort_zone_implausible
}

private fun band(x: Float, center: Float, half: Float, reps: Int) = EffortBand(
  x = x,
  lower = center - half,
  center = center,
  upper = center + half / 2f,
  reps = reps
)

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
    points = listOf(EffortPoint(0f, 100f, 10, EffortZone.COLD))
  )
}

@Preview
@Composable
private fun PreviewEffortChartColdStartOnly() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = listOf(
      EffortPoint(0f, 90f, 10, EffortZone.COLD),
      EffortPoint(1f, 95f, 10, EffortZone.COLD)
    )
  )
}

/** The three size steps and the rep ramp together, against a band they can be read against. */
@Preview
@Composable
private fun PreviewEffortChartBandAndSteps() {
  EffortChart(
    Modifier.size(340.dp, 200.dp).background(Color.White),
    points = listOf(
      EffortPoint(0f, 96f, 12, EffortZone.ON_CURVE),
      EffortPoint(1f, 88f, 15, EffortZone.BELOW),
      EffortPoint(2f, 104f, 8, EffortZone.GROWTH),
      EffortPoint(3f, 101f, 10, EffortZone.ON_CURVE),
      EffortPoint(4f, 112f, 5, EffortZone.IMPLAUSIBLE)
    ),
    bands = listOf(
      listOf(
        band(0f, 97f, 5f, 12),
        band(1f, 98f, 5f, 15),
        band(2f, 99f, 5f, 8),
        band(3f, 100f, 5f, 10),
        band(4f, 101f, 5f, 5)
      )
    ),
    yLabels = listOf(88f to "88", 112f to "112")
  )
}

/** The flare across a layoff: the band should visibly widen where the model stops knowing. */
@Preview
@Composable
private fun PreviewEffortChartPostLayoffFlare() {
  EffortChart(
    Modifier.size(340.dp, 200.dp).background(Color.White),
    points = listOf(
      EffortPoint(0f, 130f, 8, EffortZone.ON_CURVE),
      EffortPoint(1f, 133f, 8, EffortZone.ON_CURVE),
      EffortPoint(2f, 136f, 8, EffortZone.ON_CURVE),
      EffortPoint(3f, 105f, 10, EffortZone.ON_CURVE),
      EffortPoint(4f, 108f, 10, EffortZone.GROWTH)
    ),
    bands = listOf(
      listOf(
        band(0f, 129f, 6f, 8),
        band(1f, 132f, 6f, 8),
        band(2f, 135f, 6f, 8),
        band(3f, 104f, 34f, 10),
        band(4f, 106f, 7f, 10)
      )
    ),
    gapMarks = listOf(2.5f),
    xLabels = listOf(0f to "Aug '24", 3f to "Aug '26"),
    yLabels = listOf(105f to "105", 136f to "136")
  )
}

/** Bootstrap (dashed) handing off to the real fit (solid) at x=2. */
@Preview
@Composable
private fun PreviewEffortChartBandHandoff() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = listOf(
      EffortPoint(0f, 90f, 10, EffortZone.COLD),
      EffortPoint(1f, 95f, 10, EffortZone.ON_CURVE),
      EffortPoint(2f, 98f, 10, EffortZone.ON_CURVE),
      EffortPoint(3f, 100f, 9, EffortZone.ON_CURVE),
      EffortPoint(4f, 103f, 8, EffortZone.GROWTH)
    ),
    dashedBands = listOf(listOf(band(1f, 94f, 5f, 10), band(2f, 97f, 5f, 10))),
    bands = listOf(
      listOf(band(2f, 97f, 5f, 10), band(3f, 99f, 5f, 9), band(4f, 101f, 5f, 8))
    )
  )
}

/** Unloadable work: every dot at zero while the band climbs from below the axis toward a plate. */
@Preview
@Composable
private fun PreviewEffortChartBodyweightClimb() {
  EffortChart(
    Modifier.size(340.dp, 200.dp).background(Color.White),
    points = (0..5).map { EffortPoint(it.toFloat(), 0f, 11 + it, EffortZone.ON_CURVE) },
    bands = listOf(
      (0..5).map { band(it.toFloat(), -3.5f + it * 0.9f, 5f, 11 + it) }
    ),
    yLabels = listOf(0f to "0")
  )
}

@Preview
@Composable
private fun PreviewEffortChartCompactStrip() {
  EffortChart(
    Modifier.size(150.dp, 70.dp).background(Color.White),
    compact = true,
    points = listOf(
      EffortPoint(0f, 100f, 10, EffortZone.ON_CURVE),
      EffortPoint(1f, 100f, 10, EffortZone.ON_CURVE),
      EffortPoint(2f, 95f, 12, EffortZone.BELOW),
      EffortPoint(3f, 105f, 8, EffortZone.GROWTH),
      EffortPoint(4f, 105f, 8, EffortZone.GROWTH, emphasized = true)
    ),
    bands = listOf(
      listOf(
        band(0f, 99f, 5f, 10),
        band(1f, 100f, 5f, 10),
        band(2f, 100f, 5f, 12),
        band(3f, 101f, 5f, 8),
        band(4f, 102f, 5f, 8)
      )
    ),
    gapMarks = listOf(1.5f),
    yLabels = listOf(95f to "95", 105f to "105")
  )
}

/** Regression check for the plot-rect inset: max-size bubbles must not touch the box edge. */
@Preview
@Composable
private fun PreviewEffortChartCornerPoints() {
  EffortChart(
    Modifier.size(300.dp).background(Color.White),
    points = listOf(
      EffortPoint(0f, 0f, 10, EffortZone.GROWTH),
      EffortPoint(0f, 100f, 10, EffortZone.GROWTH),
      EffortPoint(10f, 0f, 10, EffortZone.GROWTH),
      EffortPoint(10f, 100f, 10, EffortZone.GROWTH)
    )
  )
}

@Preview
@Composable
private fun PreviewEffortLegend() {
  EffortLegend(Modifier.background(Color.White).padding(8.dp), repRange = 5..20)
}
