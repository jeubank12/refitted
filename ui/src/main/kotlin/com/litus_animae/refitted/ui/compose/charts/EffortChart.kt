package com.litus_animae.refitted.ui.compose.charts

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint as NativePaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.math.roundToInt
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
 *
 * [projected] marks a not-yet-completed, hypothetical set (e.g. the strip previewing where the
 * currently-dialed-in weight/reps would land) - colored/sized exactly like a real dot, but ringed
 * with a dashed rather than solid outline, so it reads as provisional without needing a duller
 * fill to say the same thing twice. Mutually exclusive with [emphasized] in practice - nothing
 * marks a projection as "the set you just did".
 */
data class EffortPoint(
  val x: Float,
  val weight: Float,
  val size: Float,
  val zone: EffortZone,
  val z: Double? = null,
  val emphasized: Boolean = false,
  val projected: Boolean = false
)

private val LabelPadding = 4.dp
private val GapMarkDash = 4.dp
private val EmphasisRingGap = 3.dp
private val EmphasisRingWidth = 1.5.dp
private val BandFadeInset = 4.dp
private val FadeOutlineWidth = 1.dp
private val DebugVertexRadius = 3.dp

// Tuned against PreviewEffortChartGradientBandingTester: 8 divisions stayed clean at fade widths
// down to ~30px, well under horizontalFadePx's actual size, so it has margin at this fade's real
// (larger) width too.
private const val FadeOutSteps = 8

// Fixed, theme-independent colors for showBandVertices - deliberately loud/high-contrast rather
// than derived from the chart's own palette, since these markers exist to stand out from
// whatever they're overlaid on, not to blend in.
private val DebugTopVertexColor = Color(0xFFFF1744)
private val DebugBottomVertexColor = Color(0xFF2979FF)
private val DebugSyntheticVertexColor = Color(0xFFFFEA00)

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
  // Where the funnel's leading fade-in should treat as its zero-height origin: the caller's
  // actual first set, in the same x/weight coordinates as [points] - not necessarily the
  // leftmost *plotted* point, since a width-driven window (see SetTrendStrip) can truncate older
  // sets off the visible domain entirely. Deliberately excluded from the chart's own auto-ranging
  // (unlike [points]/[trend]/the band lists) so a far-off-domain origin fades in off-canvas,
  // clipped by the composable's own bounds, rather than stretching the visible plot to fit it.
  // Falls back to the leftmost point in [points] when absent.
  bandOrigin: Pair<Float, Float>? = null,
  compact: Boolean = false,
  xLabels: List<Pair<Float, String>> = emptyList(),
  yLabels: List<Pair<Float, String>> = emptyList(),
  // A second set of y-axis labels drawn on the right edge instead of the left - for callers
  // that want two independent y readings at once (e.g. observed min/max on the left, the
  // funnel band's own extremes on the right) rather than merging them into one crowded list.
  // Empty by default and costs nothing unused.
  yLabelsRight: List<Pair<Float, String>> = emptyList(),
  gapMarks: List<Float> = emptyList(),
  // Debug aid: marks every real bandTop/bandBottom vertex plus the synthetic lead-in/fade-out
  // points the mesh adds on top of them, so the funnel's actual control points can be inspected
  // directly instead of inferred from the rendered gradient. Off by default and costs nothing
  // unused - see StripChartTesterActivity for the only caller that flips it on.
  showBandVertices: Boolean = false,
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

  // Excludes x<0 samples (the band's off-screen leading edge - see bandOrigin/the mesh loop
  // below) from the visible domain entirely - they exist to feed the mesh's interpolation, not to
  // stretch the plotted scale to fit data the user can't see.
  val onDomain = remember(trend, dashedTrend, bandTop, bandBottom, bandMid) {
    (trend + dashedTrend + bandTop + bandBottom + bandMid).flatten().filter { it.first >= 0f }
  }
  val minX = remember(points, onDomain) {
    minOf(points.minOf { it.x }, onDomain.minOfOrNull { it.first } ?: Float.POSITIVE_INFINITY)
  }
  val maxX = remember(points, onDomain) {
    maxOf(points.maxOf { it.x }, onDomain.maxOfOrNull { it.first } ?: Float.NEGATIVE_INFINITY)
  }
  val minY = remember(points, onDomain) {
    minOf(points.minOf { it.weight }, onDomain.minOfOrNull { it.second } ?: Float.POSITIVE_INFINITY)
  }
  val maxY = remember(points, onDomain) {
    maxOf(
      points.maxOf { it.weight },
      onDomain.maxOfOrNull { it.second }
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
  val debugVertexRadiusPx = with(density) { DebugVertexRadius.toPx() }

  val textMeasurer = rememberTextMeasurer()
  val labelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
  val xLabelLayouts = remember(xLabels, labelStyle) {
    xLabels.map { (x, text) -> x to textMeasurer.measure(text, labelStyle) }
  }
  val yLabelLayouts = remember(yLabels, labelStyle) {
    yLabels.map { (y, text) -> y to textMeasurer.measure(text, labelStyle) }
  }
  val yLabelLayoutsRight = remember(yLabelsRight, labelStyle) {
    yLabelsRight.map { (y, text) -> y to textMeasurer.measure(text, labelStyle) }
  }
  val leftGutter = if (yLabelLayouts.isEmpty()) 0f else {
    yLabelLayouts.maxOf { it.second.size.width } + labelPaddingPx
  }
  val rightGutter = if (yLabelLayoutsRight.isEmpty()) 0f else {
    yLabelLayoutsRight.maxOf { it.second.size.width } + labelPaddingPx
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
    val plotRight = (size.width - maxR - rightGutter).coerceAtLeast(plotLeft)
    val plotTop = maxR.coerceAtMost(size.height / 2f)
    val plotBottom = (size.height - maxR - bottomGutter).coerceAtLeast(plotTop)

    fun px(x: Float) = lerp(plotLeft, plotRight, nx(x))
    fun py(y: Float) = lerp(plotBottom, plotTop, ny(y))

    // Reused as the band's trailing fade-out width (see the mesh loop below) - proportional to
    // the bubble size already tuned for this chart's density, rather than a new tunable.
    val horizontalFadePx = maxPx

    // The leading fade-in's zero-height origin (see the mesh loop below): [bandOrigin] when the
    // caller supplied one (the caller's true first set, however far off-domain that is), else the
    // leftmost plotted point - real coordinates either way, not a clamped/guarded position, so an
    // off-domain origin fades in off-canvas rather than being pulled onto it.
    val leadOrigin = bandOrigin ?: points.minByOrNull { it.x }?.let { it.x to it.weight }

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
      // Whether there's still real history further back than this run's first sample that the
      // fade should represent growing out of - NOT simply "did the run reach a negative x at
      // all." leadIn (see SetTrendStrip) can find real off-screen data while cold history still
      // sits further back than that (a lone real set squeezed between a cold run and the window),
      // in which case that one real sample still needs a fade converging into it rather than
      // starting there as a hard, un-faded edge. Only when the run's first sample already reaches
      // all the way back to (or past) leadOrigin - the caller's true first-ever set - is there
      // truly nothing earlier left to fade in from.
      val needsLeadFade = leadOrigin != null && leadOrigin.first < top[0].first
      val leadCols = if (needsLeadFade) 1 else 0
      // leadCols (0 or 1) bookends the real ones on the left when needed; the trailing fade-out
      // always gets one more column past the run's last sample, carrying that end's own weights
      // (so the band's vertical shape doesn't kink) - the column itself is drawn fully opaque
      // (see the fade-out-prime writeColumn call below), with the actual fade to nothing applied
      // afterward as a shader-based DST_IN mask rather than baked into vertex alpha. Vertex-color
      // alpha interpolation in drawVertices produces a real diagonal color-darkening artifact
      // (confirmed via isolated flat-band previews - see PreviewEffortChartFlatBandHorizontal-
      // VertexAlphaFade vs ...ShaderAlphaFade); a shader gradient doesn't have that problem. Both
      // this and a synthetic lead-in naturally get clipped by the composable's own bounds when
      // their reach runs past it.
      val cols = n + leadCols + 1
      val verts = FloatArray(cols * 5 * 2)
      val colors = IntArray(cols * 5)

      // 5 rows per column. Below the on-curve stop there's no separate "cold" color any more -
      // the on-curve blue itself just fades out (alpha only, same RGB) all the way down to the
      // band's bottom edge, so "under expectation" reads as this color thinning out rather than
      // a second hue taking over. The top still fades outward a few px *beyond* its edge (see
      // [BandFadeInset]) since a hard color->color edge there read fine, unlike the bottom.
      fun writeColumn(col: Int, xPx: Float, topWeight: Float, bottomWeight: Float, midWeight: Float, alphaScale: Float) {
        val span = topWeight - bottomWeight
        val fracTrend = if (span <= 0f) 0.5f else ((midWeight - bottomWeight) / span).coerceIn(0f, 1f)
        val fracGrowth = lerp(fracTrend, 1f, 0.5f)
        val bottomPx = py(bottomWeight)
        val topPx = py(topWeight)
        val onCurvePx = lerp(bottomPx, topPx, fracTrend)
        val growthPx = lerp(bottomPx, topPx, fracGrowth)
        val baseColorArgb = baseColor.copy(alpha = bandAlpha * alphaScale).toArgb()
        val peakColorArgb = peakColor.copy(alpha = bandAlpha * alphaScale).toArgb()
        val punishedColorArgb = punishedColor.copy(alpha = bandAlpha * alphaScale).toArgb()
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
        val base = col * 5
        for (row in rowY.indices) {
          verts[(base + row) * 2] = xPx
          verts[(base + row) * 2 + 1] = rowY[row]
          colors[base + row] = rowColors[row]
        }
      }

      // Every real column except the last renders as-is. The last one (typically the live/
      // projected point) is handled specially below - see the "provisional-prime"/"fade-out-
      // prime" comment - so it's excluded here rather than written and then overwritten.
      for (j in 0 until n - 1) {
        val (x, topWeight) = top[j]
        writeColumn(j + leadCols, px(x), topWeight, bottom[j].second, mid[j].second, alphaScale = 1f)
      }
      if (needsLeadFade) {
        // A zero-height point at [leadOrigin]'s coordinate (non-null - needsLeadFade already
        // required it), not a translated copy of the run's own first sample's shape - the mesh
        // naturally widens from that point out to the run's real top/bottom/mid as it reaches the
        // first real column, rather than sliding an unrelated shape sideways.
        val (leadX, leadWeight) = leadOrigin
        writeColumn(0, px(leadX), leadWeight, leadWeight, leadWeight, alphaScale = 0f)
      }

      // The run's true last sample (usually the live/projected point) often sits right where a
      // stagnation-narrowed low anchor (see EffortModel.lowAnchorReps) has pulled bandBottom
      // close to bandTop - rendered at its own x at full opacity, that reads as a sudden pinch
      // right at a real, fully-visible column. "fade-out-prime" carries that same real value but
      // relocated to where the mesh fades to nothing (xLast + horizontalFadePx, via the DST_IN
      // mask below) - so the pinch still exists, but only ever at (or near) zero alpha, never
      // fully seen. "provisional-prime" takes over the last sample's own x at full opacity
      // instead, valued as a straight-line interpolation between the second-to-last real column
      // and fade-out-prime - i.e. the same slope the real data was already on, just stretched
      // over the extra horizontalFadePx of run instead of snapping to the pinched value within
      // one column's width. This also fixes the previous flat provisional->fade-out block's
      // uneven color steps as a side effect: top/bottom/mid now interpolate along one line
      // instead of jumping independently.
      val (xLast, topLast) = top[n - 1]
      val bottomLast = bottom[n - 1].second
      val midLast = mid[n - 1].second
      val fadeOutPrimeXPx = px(xLast) + horizontalFadePx

      val (xFinal, topFinal) = top[n - 2]
      val bottomFinal = bottom[n - 2].second
      val midFinal = mid[n - 2].second
      val xFinalPx = px(xFinal)

      val provisionalPrimeXPx = px(xLast)
      val fadeSpanPx = fadeOutPrimeXPx - xFinalPx
      val t = if (fadeSpanPx != 0f) (provisionalPrimeXPx - xFinalPx) / fadeSpanPx else 1f
      val topProvisionalPrime = lerp(topFinal, topLast, t)
      val bottomProvisionalPrime = lerp(bottomFinal, bottomLast, t)
      val midProvisionalPrime = lerp(midFinal, midLast, t)

      writeColumn(
        n - 1 + leadCols,
        provisionalPrimeXPx,
        topProvisionalPrime,
        bottomProvisionalPrime,
        midProvisionalPrime,
        alphaScale = 1f
      )
      writeColumn(cols - 1, fadeOutPrimeXPx, topLast, bottomLast, midLast, alphaScale = 1f)

      // 2 triangles per grid cell (4 row-bands x (cols - 1) column-gaps), listed explicitly
      // rather than as a single strip - a strip would need degenerate bridging triangles between
      // runs, and this chart already draws one run at a time.
      val indices = ShortArray((cols - 1) * 4 * 6)
      var ii = 0
      for (j in 0 until cols - 1) {
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
        val bounds = RectF(0f, 0f, size.width, size.height)
        val layerId = canvas.nativeCanvas.saveLayer(bounds, null)
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
        // Fades the trailing edge (provisionalPrimeXPx to fadeOutPrimeXPx) to nothing via a
        // shader-based DST_IN mask instead of vertex alpha - see the writeColumn calls above for
        // why. CLAMP holds the mask at fully opaque before provisionalPrimeXPx and fully
        // transparent past fadeOutPrimeXPx, so nothing earlier in this run is affected.
        // Smoothstep-eased stops (not a plain 2-stop linear ramp) avoid a Mach band at the
        // ramp-to-plateau edges - see smoothstepAlphaStops's kdoc; 8 divisions was tuned against
        // PreviewEffortChartGradientBandingTester to stay clean at this fade's actual pixel width.
        val (fadeColors, fadePositions) = smoothstepAlphaStops(FadeOutSteps)
        val fadeMaskPaint = NativePaint().apply {
          shader = LinearGradient(
            provisionalPrimeXPx, 0f, fadeOutPrimeXPx, 0f,
            fadeColors, fadePositions, Shader.TileMode.CLAMP
          )
          xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.nativeCanvas.drawRect(bounds, fadeMaskPaint)
        canvas.nativeCanvas.restoreToCount(layerId)
      }

      if (showBandVertices) {
        // Real vertices actually rendered as-is: every sample except the run's last, which
        // writeColumn above replaces with provisional-prime rather than its own raw value -
        // marking the raw top[n-1]/bottom[n-1] here would show a point the mesh no longer draws.
        for (j in 0 until n - 1) {
          drawCircle(DebugTopVertexColor, debugVertexRadiusPx, Offset(px(top[j].first), py(top[j].second)))
          drawCircle(DebugBottomVertexColor, debugVertexRadiusPx, Offset(px(bottom[j].first), py(bottom[j].second)))
        }
        // Synthetic points the mesh adds on top of the real data (see needsLeadFade and
        // provisional-prime/fade-out-prime above) - hollow rings, distinct from the filled
        // real-vertex dots. provisional-prime is drawn filled (it's still full alpha, unlike the
        // true fade-out point) but in the synthetic color, since its value is interpolated, not
        // the run's own raw sample.
        if (needsLeadFade) {
          val (leadX, leadWeight) = leadOrigin
          drawCircle(
            DebugSyntheticVertexColor,
            debugVertexRadiusPx,
            Offset(px(leadX), py(leadWeight)),
            style = Stroke(width = fadeOutlinePx)
          )
        }
        drawCircle(DebugSyntheticVertexColor, debugVertexRadiusPx, Offset(provisionalPrimeXPx, py(topProvisionalPrime)))
        drawCircle(DebugSyntheticVertexColor, debugVertexRadiusPx, Offset(provisionalPrimeXPx, py(bottomProvisionalPrime)))
        drawCircle(
          DebugSyntheticVertexColor,
          debugVertexRadiusPx,
          Offset(fadeOutPrimeXPx, py(topLast)),
          style = Stroke(width = fadeOutlinePx)
        )
        drawCircle(
          DebugSyntheticVertexColor,
          debugVertexRadiusPx,
          Offset(fadeOutPrimeXPx, py(bottomLast)),
          style = Stroke(width = fadeOutlinePx)
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
      // A projected point is colored from where its own weight falls within the funnel band at
      // its x, not z - the band there is causally derived from real history alone (see
      // SetTrendStrip's kdoc), so this is a real, stable read of "where does this land," and it
      // guarantees the dot always agrees with the gradient drawn under it. z is a good match for
      // an already-*completed* set (it says how that specific rep/rest/recency combination
      // compared to expectation) but a poor one for a hypothetical - a projection swinging to an
      // aggressive rep count can produce a z on a completely different footing than the band's
      // own rep-target anchors, reading as a jump relative to the band even when the weight
      // itself sits right where the band would predict. Falls through to the normal z/zone path
      // when there's no matching band tail to read (e.g. still COLD).
      val bandColor = if (point.projected) {
        val top = bandTop.lastOrNull()?.lastOrNull()?.second
        val bottom = bandBottom.lastOrNull()?.lastOrNull()?.second
        val mid = bandMid.lastOrNull()?.lastOrNull()?.second
        if (top != null && bottom != null && mid != null) {
          bandRelativeColor(point.weight, top, bottom, mid, baseColor, peakColor, punishedColor)
        } else {
          null
        }
      } else {
        null
      }
      // Bubbles with a z-score read as solid, unlike the funnel band behind them - force full
      // opacity here rather than in effortColor itself, whose below-on-curve anchors carry real
      // white-blended RGB (not alpha) specifically so they stay visible once forced opaque. A
      // COLD dot (no expectation yet, z null) has no such RGB fade - it keeps zoneColor's true
      // alpha instead, since there's no band underneath it to fight for legibility against.
      val color = bandColor
        ?: point.z?.let { effortColor(it, baseColor, peakColor, punishedColor, coldColor).copy(alpha = 1f) }
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
      if (point.projected) {
        // Dashed, matching dashedTrend's own "dashed = not the real/committed thing" language -
        // a projected dot and the bootstrap trend line are both stand-ins, so they read as the
        // same kind of provisional rather than inventing a second visual vocabulary.
        drawCircle(
          emphasisColor,
          radius = diameter / 2f + emphasisGapPx,
          center = center,
          style = Stroke(
            width = emphasisWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(gapDashPx / 2f, gapDashPx / 2f))
          )
        )
      }
    }

    yLabelLayouts.forEach { (y, layout) ->
      drawText(
        layout,
        topLeft = Offset(plotLeft - maxR - labelPaddingPx - layout.size.width, py(y) - layout.size.height / 2f)
      )
    }
    yLabelLayoutsRight.forEach { (y, layout) ->
      drawText(
        layout,
        topLeft = Offset(plotRight + maxR + labelPaddingPx, py(y) - layout.size.height / 2f)
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

/** Colors a point the same way the funnel band's own gradient mesh would at [weight]'s height
 * between [bottomWeight] and [topWeight] - see the caller in [EffortChart]'s point-drawing loop.
 * Mirrors the mesh's own row semantics (same [midWeight]-derived on-curve/growth breakpoints as
 * the mesh loop, so the two can't diverge): flat [baseColor] at and below the on-curve stop (the
 * mesh has no second hue fading in there either - see the mesh loop's own comment), [baseColor]
 * to [peakColor] up to the growth stop, then [peakColor] to [punishedColor] beyond it to the top
 * edge, clamped flat past either edge (a hypothetical past the heavy/4-rep edge reads exactly
 * like the mesh's own top edge does, not some off-scale extreme).
 */
private fun bandRelativeColor(
  weight: Float,
  topWeight: Float,
  bottomWeight: Float,
  midWeight: Float,
  baseColor: Color,
  peakColor: Color,
  punishedColor: Color
): Color {
  val span = topWeight - bottomWeight
  if (span <= 0f) return baseColor
  val fracTrend = ((midWeight - bottomWeight) / span).coerceIn(0f, 1f)
  val fracGrowth = lerp(fracTrend, 1f, 0.5f)
  val frac = ((weight - bottomWeight) / span).coerceIn(0f, 1f)
  val anchors = listOf(0f to baseColor, fracTrend to baseColor, fracGrowth to peakColor, 1f to punishedColor)
  for (i in 0 until anchors.size - 1) {
    val (fLo, colorLo) = anchors[i]
    val (fHi, colorHi) = anchors[i + 1]
    if (frac <= fHi) {
      val t = if (fHi <= fLo) 0f else (frac - fLo) / (fHi - fLo)
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

/** Isolates whether the mesh's diagonal color banding comes from tilted (sloped) quads being
 * triangulated, by removing all slope: bandTop/bandBottom/bandMid are each a constant value
 * across every column, so every mesh cell is an axis-aligned rectangle. A rectangle split into
 * 2 triangles is mathematically guaranteed to reproduce an exact linear gradient with no seam -
 * so if a diagonal is still visible here, the tilted-quad theory is wrong and something else
 * (e.g. non-premultiplied alpha interpolation in drawVertices) is the real cause. */
@Preview
@Composable
private fun PreviewEffortChartFlatBandArtifactIsolation() {
  RefittedTheme(darkTheme = darkTheme) {
    EffortChart(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      points = (0..6).map { EffortPoint(it.toFloat(), 100f, 0.45f, EffortZone.ON_CURVE) },
      bandTop = listOf((0..6).map { it.toFloat() to 120f }),
      bandBottom = listOf((0..6).map { it.toFloat() to 80f }),
      bandMid = listOf((0..6).map { it.toFloat() to 100f })
    )
  }
}

/**
 * Same triangle-mesh drawVertices rendering as [PreviewEffortChartFlatBandArtifactIsolation],
 * but every vertex is fully opaque - hue-only interpolation, no alpha baked into any vertex
 * color at all. The top/bottom fade-to-transparent look that the real mesh gets from its
 * alpha-0 rows is reproduced afterward instead, via a real [LinearGradient] (an SkShader,
 * documented to interpolate premultiplied correctly - unlike drawVertices' own straight-alpha
 * per-vertex interpolation) composited over the opaque mesh with [PorterDuff.Mode.DST_IN].
 *
 * If this renders clean where the vertex-alpha version banded, it confirms vertex-color alpha
 * interpolation as the culprit, and this pattern - opaque mesh, alpha applied as a separate
 * shader-based mask - as the fix to carry into the real chart.
 */
@Preview
@Composable
private fun PreviewEffortChartFlatBandHueOnlyWithAlphaOverlay() {
  RefittedTheme(darkTheme = darkTheme) {
    val baseColor = MaterialTheme.colorScheme.primary
    val peakColor = ExtendedTheme.colors.goodAttention.color
    val punishedColor = ExtendedTheme.colors.timerAmber.color
    val meshPaint = remember { NativePaint() }
    Canvas(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(8.dp)
    ) {
      val bottomPx = size.height - 8f
      val topPx = 8f
      val midPx = lerp(bottomPx, topPx, 0.6f)

      val cols = 7
      val verts = FloatArray(cols * 3 * 2)
      val colors = IntArray(cols * 3)
      val rowColors = intArrayOf(baseColor.toArgb(), peakColor.toArgb(), punishedColor.toArgb())
      for (col in 0 until cols) {
        val xPx = lerp(0f, size.width, col / (cols - 1).toFloat())
        val rowY = floatArrayOf(bottomPx, midPx, topPx)
        val base = col * 3
        for (row in rowY.indices) {
          verts[(base + row) * 2] = xPx
          verts[(base + row) * 2 + 1] = rowY[row]
          colors[base + row] = rowColors[row]
        }
      }
      val indices = ShortArray((cols - 1) * 2 * 6)
      var ii = 0
      for (j in 0 until cols - 1) {
        for (row in 0 until 2) {
          val tl = (j * 3 + row).toShort()
          val bl = (j * 3 + row + 1).toShort()
          val tr = ((j + 1) * 3 + row).toShort()
          val br = ((j + 1) * 3 + row + 1).toShort()
          indices[ii++] = tl; indices[ii++] = bl; indices[ii++] = tr
          indices[ii++] = bl; indices[ii++] = br; indices[ii++] = tr
        }
      }
      drawIntoCanvas { canvas ->
        val bounds = RectF(0f, 0f, size.width, size.height)
        val layerId = canvas.nativeCanvas.saveLayer(bounds, null)
        canvas.nativeCanvas.drawVertices(
          NativeCanvas.VertexMode.TRIANGLES, verts.size, verts, 0, null, 0, colors, 0, indices, 0, indices.size, meshPaint
        )
        val maskPaint = NativePaint().apply {
          shader = LinearGradient(
            0f, bottomPx, 0f, topPx,
            intArrayOf(NativeColor.TRANSPARENT, NativeColor.BLACK, NativeColor.BLACK, NativeColor.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 0.6f, 1f),
            Shader.TileMode.CLAMP
          )
          xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.nativeCanvas.drawRect(bounds, maskPaint)
        canvas.nativeCanvas.restoreToCount(layerId)
      }
    }
  }
}

/**
 * Isolates the *horizontal* fade specifically - the direction the original screenshots that
 * started this investigation were actually about (the right-edge fade-out-prime region), not
 * the vertical top/bottom fade [PreviewEffortChartFlatBandHueOnlyWithAlphaOverlay] tested. Same
 * flat, hue-only mesh, fully opaque everywhere except the last 2 of 7 columns, whose vertex
 * alpha is baked directly into the color ints ramping toward 0 - i.e. exactly how production's
 * fade-out-prime column works today, just isolated from any vertical alpha or slope. If a
 * diagonal shows up here, horizontal vertex-alpha interpolation is (at least part of) the real
 * culprit independent of the vertical case already tested.
 */
@Preview
@Composable
private fun PreviewEffortChartFlatBandHorizontalVertexAlphaFade() {
  RefittedTheme(darkTheme = darkTheme) {
    val baseColor = MaterialTheme.colorScheme.primary
    val peakColor = ExtendedTheme.colors.goodAttention.color
    val punishedColor = ExtendedTheme.colors.timerAmber.color
    val meshPaint = remember { NativePaint() }
    Canvas(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(8.dp)
    ) {
      val bottomPx = size.height - 8f
      val topPx = 8f
      val midPx = lerp(bottomPx, topPx, 0.6f)

      val cols = 7
      // Full alpha through column 4, ramping to 0 across the last 2 columns - baked straight
      // into the vertex color ints, matching how fade-out-prime bakes alpha today.
      val colAlpha = (0 until cols).map { col -> ((cols - 1 - col).toFloat() / 2f).coerceIn(0f, 1f) }

      val verts = FloatArray(cols * 3 * 2)
      val colors = IntArray(cols * 3)
      for (col in 0 until cols) {
        val xPx = lerp(0f, size.width, col / (cols - 1).toFloat())
        val rowY = floatArrayOf(bottomPx, midPx, topPx)
        val a = colAlpha[col]
        val rowColors = intArrayOf(
          baseColor.copy(alpha = a).toArgb(),
          peakColor.copy(alpha = a).toArgb(),
          punishedColor.copy(alpha = a).toArgb()
        )
        val base = col * 3
        for (row in rowY.indices) {
          verts[(base + row) * 2] = xPx
          verts[(base + row) * 2 + 1] = rowY[row]
          colors[base + row] = rowColors[row]
        }
      }
      val indices = ShortArray((cols - 1) * 2 * 6)
      var ii = 0
      for (j in 0 until cols - 1) {
        for (row in 0 until 2) {
          val tl = (j * 3 + row).toShort()
          val bl = (j * 3 + row + 1).toShort()
          val tr = ((j + 1) * 3 + row).toShort()
          val br = ((j + 1) * 3 + row + 1).toShort()
          indices[ii++] = tl; indices[ii++] = bl; indices[ii++] = tr
          indices[ii++] = bl; indices[ii++] = br; indices[ii++] = tr
        }
      }
      drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawVertices(
          NativeCanvas.VertexMode.TRIANGLES, verts.size, verts, 0, null, 0, colors, 0, indices, 0, indices.size, meshPaint
        )
      }
    }
  }
}

/**
 * Same setup as [PreviewEffortChartFlatBandHorizontalVertexAlphaFade], but the horizontal fade
 * is applied afterward via a horizontal [LinearGradient] + [PorterDuff.Mode.DST_IN] mask instead
 * of vertex alpha - every vertex in the mesh itself is fully opaque. If this one renders clean
 * where the vertex-alpha version bands, the shader-mask fix generalizes to the horizontal
 * direction too, not just the vertical one already confirmed.
 */
@Preview
@Composable
private fun PreviewEffortChartFlatBandHorizontalShaderAlphaFade() {
  RefittedTheme(darkTheme = darkTheme) {
    val baseColor = MaterialTheme.colorScheme.primary
    val peakColor = ExtendedTheme.colors.goodAttention.color
    val punishedColor = ExtendedTheme.colors.timerAmber.color
    val meshPaint = remember { NativePaint() }
    Canvas(
      Modifier
        .size(300.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(8.dp)
    ) {
      val bottomPx = size.height - 8f
      val topPx = 8f
      val midPx = lerp(bottomPx, topPx, 0.6f)

      val cols = 7
      val verts = FloatArray(cols * 3 * 2)
      val colors = IntArray(cols * 3)
      val rowColors = intArrayOf(baseColor.toArgb(), peakColor.toArgb(), punishedColor.toArgb())
      for (col in 0 until cols) {
        val xPx = lerp(0f, size.width, col / (cols - 1).toFloat())
        val rowY = floatArrayOf(bottomPx, midPx, topPx)
        val base = col * 3
        for (row in rowY.indices) {
          verts[(base + row) * 2] = xPx
          verts[(base + row) * 2 + 1] = rowY[row]
          colors[base + row] = rowColors[row]
        }
      }
      val indices = ShortArray((cols - 1) * 2 * 6)
      var ii = 0
      for (j in 0 until cols - 1) {
        for (row in 0 until 2) {
          val tl = (j * 3 + row).toShort()
          val bl = (j * 3 + row + 1).toShort()
          val tr = ((j + 1) * 3 + row).toShort()
          val br = ((j + 1) * 3 + row + 1).toShort()
          indices[ii++] = tl; indices[ii++] = bl; indices[ii++] = tr
          indices[ii++] = bl; indices[ii++] = br; indices[ii++] = tr
        }
      }
      drawIntoCanvas { canvas ->
        val bounds = RectF(0f, 0f, size.width, size.height)
        val layerId = canvas.nativeCanvas.saveLayer(bounds, null)
        canvas.nativeCanvas.drawVertices(
          NativeCanvas.VertexMode.TRIANGLES, verts.size, verts, 0, null, 0, colors, 0, indices, 0, indices.size, meshPaint
        )
        val maskPaint = NativePaint().apply {
          shader = LinearGradient(
            size.width * 5f / 7f, 0f, size.width, 0f,
            intArrayOf(NativeColor.BLACK, NativeColor.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
          )
          xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.nativeCanvas.drawRect(bounds, maskPaint)
        canvas.nativeCanvas.restoreToCount(layerId)
      }
    }
  }
}

/** Smoothstep-eased (colors, positions) for a LinearGradient's DST_IN alpha mask, ramping from
 * fully opaque at t=0 to fully transparent at t=1 across [steps] linear segments - RGB is
 * irrelevant for a DST_IN mask, only alpha matters, so every stop is packed as alpha-only black.
 * Approximating smoothstep (zero slope at both ends) rather than a single linear segment is what
 * lets the ramp meet a flat plateau tangentially instead of at a hard corner - see
 * PreviewEffortChartGradientBandingTester's kdoc for why a hard corner there reads as a visible
 * Mach band. */
private fun smoothstepAlphaStops(steps: Int): Pair<IntArray, FloatArray> {
  val colors = IntArray(steps + 1)
  val positions = FloatArray(steps + 1)
  for (i in 0..steps) {
    val t = i / steps.toFloat()
    val eased = t * t * (3f - 2f * t)
    val alpha = (255 * (1f - eased)).roundToInt().coerceIn(0, 255)
    positions[i] = t
    colors[i] = alpha shl 24
  }
  return colors to positions
}

/**
 * Interactive (Android Studio Interactive Preview) harness for tuning the funnel band's alpha
 * fade edge. Renders the same hue-only (fully opaque vertex colors) triangle mesh as
 * [PreviewEffortChartFlatBandHorizontalShaderAlphaFade], with the fade applied afterward via
 * [smoothstepAlphaStops] instead of a fixed 2-stop linear ramp - fade width, position, and stop
 * count are all live sliders, so the minimum stop count needed to avoid a visible Mach band at a
 * given width can be found empirically instead of guessed. A single linear segment (steps = 1)
 * reproduces the hard-edged Mach band from [PreviewEffortChartFlatBandHorizontalShaderAlphaFade]
 * for comparison; raising steps should visibly soften it without needing to widen the fade.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 500)
@Composable
private fun PreviewEffortChartGradientBandingTester() {
  RefittedTheme(darkTheme = darkTheme) {
    var fadeStartPx by remember { mutableFloatStateOf(150f) }
    var fadeWidthPx by remember { mutableFloatStateOf(60f) }
    var steps by remember { mutableIntStateOf(1) }

    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Fade start: ${fadeStartPx.roundToInt()}px", style = MaterialTheme.typography.labelSmall)
      Slider(value = fadeStartPx, onValueChange = { fadeStartPx = it }, valueRange = 0f..300f)
      Text("Fade width: ${fadeWidthPx.roundToInt()}px", style = MaterialTheme.typography.labelSmall)
      Slider(value = fadeWidthPx, onValueChange = { fadeWidthPx = it }, valueRange = 4f..200f)
      Text("Divisions (stops): $steps", style = MaterialTheme.typography.labelSmall)
      Slider(
        value = steps.toFloat(),
        onValueChange = { steps = it.roundToInt() },
        valueRange = 1f..32f,
        steps = 30
      )

      val baseColor = MaterialTheme.colorScheme.primary
      val peakColor = ExtendedTheme.colors.goodAttention.color
      val punishedColor = ExtendedTheme.colors.timerAmber.color
      val meshPaint = remember { NativePaint() }

      Canvas(
        Modifier
          .fillMaxWidth()
          .height(220.dp)
          .background(MaterialTheme.colorScheme.surfaceContainer)
      ) {
        val cols = 20
        val bottomPx = size.height - 8f
        val topPx = 8f
        val midPx = lerp(bottomPx, topPx, 0.6f)
        val verts = FloatArray(cols * 3 * 2)
        val colors = IntArray(cols * 3)
        // Matches EffortChart's own default bandAlpha - the tester is only for the fade-out
        // segment's stop count/width, not for re-litigating the funnel's baseline translucency.
        val rowColors = intArrayOf(
          baseColor.copy(alpha = 0.45f).toArgb(),
          peakColor.copy(alpha = 0.45f).toArgb(),
          punishedColor.copy(alpha = 0.45f).toArgb()
        )
        for (col in 0 until cols) {
          val xPx = lerp(0f, size.width, col / (cols - 1).toFloat())
          val rowY = floatArrayOf(bottomPx, midPx, topPx)
          val base = col * 3
          for (row in rowY.indices) {
            verts[(base + row) * 2] = xPx
            verts[(base + row) * 2 + 1] = rowY[row]
            colors[base + row] = rowColors[row]
          }
        }
        val indices = ShortArray((cols - 1) * 2 * 6)
        var ii = 0
        for (j in 0 until cols - 1) {
          for (row in 0 until 2) {
            val tl = (j * 3 + row).toShort()
            val bl = (j * 3 + row + 1).toShort()
            val tr = ((j + 1) * 3 + row).toShort()
            val br = ((j + 1) * 3 + row + 1).toShort()
            indices[ii++] = tl; indices[ii++] = bl; indices[ii++] = tr
            indices[ii++] = bl; indices[ii++] = br; indices[ii++] = tr
          }
        }

        drawIntoCanvas { canvas ->
          val bounds = RectF(0f, 0f, size.width, size.height)
          val layerId = canvas.nativeCanvas.saveLayer(bounds, null)
          canvas.nativeCanvas.drawVertices(
            NativeCanvas.VertexMode.TRIANGLES, verts.size, verts, 0, null, 0, colors, 0, indices, 0, indices.size, meshPaint
          )
          val (maskColors, maskPositions) = smoothstepAlphaStops(steps)
          val fadeStart = fadeStartPx.coerceIn(0f, size.width)
          val fadeEnd = (fadeStartPx + fadeWidthPx).coerceIn(0f, size.width)
          val maskPaint = NativePaint().apply {
            shader = LinearGradient(fadeStart, 0f, fadeEnd, 0f, maskColors, maskPositions, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
          }
          canvas.nativeCanvas.drawRect(bounds, maskPaint)
          canvas.nativeCanvas.restoreToCount(layerId)
        }
      }
    }
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

/** A projected (not-yet-completed) dot sitting off the funnel gradient - higher weight, fewer
 * reps than the trend supports, so it should read as dimmer/dashed and clearly below-curve
 * relative to the solid, already-scored dots around it. */
@Preview
@Composable
private fun PreviewEffortChartProjectedPoint() {
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
        EffortPoint(4f, 100f, 0.85f, EffortZone.GROWTH, emphasized = true),
        EffortPoint(5f, 115f, 0.30f, EffortZone.BELOW, z = -1.5, projected = true)
      ),
      trend = listOf(listOf(3f to 96f, 4f to 98f)),
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
