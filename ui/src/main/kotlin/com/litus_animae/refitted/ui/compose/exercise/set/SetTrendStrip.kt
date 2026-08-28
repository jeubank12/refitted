package com.litus_animae.refitted.ui.compose.exercise.set

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.effort.EffortModel
import com.litus_animae.refitted.data.effort.EffortSet
import com.litus_animae.refitted.data.effort.EffortZone
import com.litus_animae.refitted.data.effort.ScoredSet
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.charts.EffortChart
import com.litus_animae.refitted.ui.compose.charts.EffortPoint
import com.litus_animae.refitted.ui.compose.charts.buildTrendRuns
import com.litus_animae.refitted.ui.compose.charts.GradientBaseColor
import com.litus_animae.refitted.ui.compose.charts.GradientColdColor
import com.litus_animae.refitted.ui.compose.charts.GradientPeakColor
import com.litus_animae.refitted.ui.compose.charts.GradientPunishedColor
import com.litus_animae.refitted.ui.compose.charts.effortChartMinSlotWidth
import com.litus_animae.refitted.ui.compose.charts.zoneColor
import com.litus_animae.refitted.ui.compose.charts.zoneLabelRes
import com.litus_animae.refitted.ui.compose.exercise.exampleExerciseSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_WINDOW = 5
private const val MAX_WINDOW = 14

// Real sets kept just before the window to give the funnel band's leading edge a real slope to
// grow out of - never plotted as bubbles. Capped rather than "every prior set" so a long history
// (recentSets fetches up to a few hundred) doesn't feed hundreds of mostly-off-screen mesh
// columns per frame; bandOrigin still anchors the fade-in to the true first set however far back
// it is.
private const val EXTRA_LEADING_POINTS = 3

/**
 * `null` until [history] has emitted for this exercise. Keyed on the flow instance so an
 * exercise change resets to `null` rather than serving the previous exercise's sets (see
 * ui/CLAUDE.md's Compose Gotchas). [todaysRecords] are this session's in-memory sets, applied
 * ahead of Room's write round-trip reaching [history].
 */
@Composable
fun rememberEffortSets(
  history: Flow<List<SetRecord>>,
  todaysRecords: List<Record>
): List<EffortSet>? {
  val recorded = remember(history) { mutableStateOf<List<SetRecord>?>(null) }
  LaunchedEffect(history) {
    withContext(Dispatchers.IO) {
      history.collect { recorded.value = it }
    }
  }
  val recordedValue = recorded.value

  // Record.completed on an in-memory set can be stale (copied from the last stored record,
  // possibly a prior day) while the SetRecord Room eventually persists stamps Instant.now() -
  // so today's sets are matched by position against however many of today's history rows have
  // already round-tripped back through recentSets, never by comparing timestamps.
  return remember(recordedValue, todaysRecords) {
    if (recordedValue == null) return@remember null
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val historyToday = recordedValue.count { it.completed.atZone(zone).toLocalDate() == today }
    val unseen = if (todaysRecords.size > historyToday) {
      todaysRecords.drop(historyToday).map { it.toEffortSet().copy(completed = Instant.now()) }
    } else {
      emptyList()
    }
    recordedValue.map { it.toEffortSet() } + unseen
  }
}

/**
 * Compact effort trend for the most recent sets, sized to however many bubbles actually fit
 * legibly in [modifier]'s measured width rather than a fixed count. [onClick] opens the full
 * set-history drawer - this strip is a glance, not the place to read the whole history.
 *
 * [projected] is a not-yet-completed, hypothetical set (e.g. the weight/reps currently dialed
 * in on the steppers below) previewed one step past [merged] - see
 * [EffortModel.scoreWithBootstrap]'s kdoc for why appending and re-scoring it can't perturb
 * anything already drawn. Ignored while [merged] is empty - a projection needs at least one real
 * set to fit against, same as the all-COLD "locked" state that already covers a bare history.
 * [EffortSet.completed] is re-stamped to strictly after every set in [merged] regardless of what
 * the caller passed - [EffortModel]'s day-grain fit sorts by that timestamp, so a caller whose
 * clock has drifted even slightly behind (e.g. a stale `remember`-captured `Instant.now()`) would
 * otherwise sort the projection to the *front* of the session instead of the end.
 */
@Composable
fun SetTrendStrip(
  modifier: Modifier = Modifier,
  merged: List<EffortSet>,
  projected: EffortSet? = null,
  onClick: () -> Unit = {},
  // Forwarded straight to EffortChart's own debug aid - see its kdoc. Off by default and costs
  // nothing unused; StripChartTester in app/src/debug is the only caller that flips it on.
  showBandVertices: Boolean = false
) {
  BoxWithConstraints(modifier) {
    val window = if (maxWidth != Dp.Infinity) {
      ((maxWidth - 12.dp) / effortChartMinSlotWidth(compact = true))
        .toInt()
        .coerceIn(MIN_WINDOW, MAX_WINDOW)
    } else {
      MIN_WINDOW
    }
    val effectiveProjected = projected.takeIf { merged.isNotEmpty() }?.let { p ->
      val latest = merged.maxOf { it.completed }
      if (p.completed > latest) p else p.copy(completed = latest.plusMillis(1))
    }

    // scoreWithBootstrap augments score()'s output rather than replacing it - every session
    // past EffortConfig.minPriorSessions behaves identically, and a first-ever session stays
    // all-COLD regardless of its own set count (no prior session exists yet to bootstrap
    // from). Kept empty (not an early return) when there's no data yet - the card and its
    // "locked" header still render below so the strip reserves its space and shows the same
    // locked message from the very first render, rather than popping in once data exists.
    //
    // effectiveProjected, when present, is appended as one more trailing set before scoring and
    // sliced back off before windowing - windowed (and everything built from it below) is left
    // exactly as it'd be without a projection; projectedScored is that trailing entry alone.
    data class TrendWindow(
      val windowed: List<ScoredSet>,
      val projectedScored: ScoredSet?,
      // Fallback for when there's no real preceding data to extend the band into (see leadIn) -
      // the actual first set of the whole history, so the band still visibly grows from a real
      // coordinate instead of the run's own first sample appearing to start from nowhere.
      val bandOrigin: Pair<Float, Float>?,
      // Up to [EXTRA_LEADING_POINTS] real sets immediately before windowed, oldest first - feeds
      // the band's leading edge (see its construction below) a real slope without ever getting a
      // plotted bubble. When this can't reach far enough back to cover all earlier history (an
      // isolated real point with cold history still behind it, or simply the very start of the
      // log), [bandOrigin] takes over the fade-in - see EffortChart's bandOrigin/needsLeadFade
      // kdoc.
      val leadIn: List<ScoredSet>
    )

    val trendWindow = if (merged.isEmpty()) {
      TrendWindow(emptyList(), null, null, emptyList())
    } else {
      remember(merged, window, effectiveProjected) {
        val scored = EffortModel.scoreWithBootstrap(
          if (effectiveProjected != null) merged + effectiveProjected else merged
        )
        val committed = if (effectiveProjected != null) scored.sets.dropLast(1) else scored.sets
        val windowedSets = committed.takeLast(window)
        val leadIn = committed.dropLast(windowedSets.size).takeLast(EXTRA_LEADING_POINTS)
        val origin = committed.firstOrNull()?.let {
          -(committed.size - windowedSets.size).toFloat() to it.source.weight.toFloat()
        }
        TrendWindow(
          windowedSets,
          if (effectiveProjected != null) scored.sets.lastOrNull() else null,
          origin,
          leadIn
        )
      }
    }
    val windowed = trendWindow.windowed
    val projectedScored = trendWindow.projectedScored
    val bandOrigin = trendWindow.bandOrigin
    val leadIn = trendWindow.leadIn

    Card(Modifier.fillMaxSize().clickable(onClick = onClick), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
      Column(Modifier.fillMaxSize()) {
        // Doubles as the chart's only legend: it names the color of the bubble the user
        // just earned, in the moment they earn it, rather than a static key for all five.
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(stringResource(R.string.effort_label), style = MaterialTheme.typography.labelSmall)
          val latestZone = windowed.lastOrNull()?.zone
          // weight(1f) claims whatever the "Effort" label didn't, so the countdown text (much
          // longer than a zone label) is guaranteed the 8dp gap and a bound on its own width
          // instead of butting straight up against "Effort" under SpaceBetween.
          Row(
            Modifier
              .weight(1f)
              .padding(start = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (latestZone == null || latestZone == EffortZone.COLD) {
              // No sets logged for this exercise yet, or (once logged) still a first-ever
              // session - even one prior session with enough sets unlocks the bootstrap trend
              // immediately, so this is a real state worth naming rather than an unexplained
              // "New" label or leaving the header blank.
              Text(
                stringResource(R.string.strip_trend_locked),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
              )
            } else {
              Box(
                Modifier
                  .size(8.dp)
                  .background(
                    zoneColor(
                      latestZone,
                      GradientBaseColor,
                      GradientPeakColor,
                      GradientPunishedColor,
                      GradientColdColor
                    ),
                    CircleShape
                  )
              )
              Spacer(Modifier.width(4.dp))
              Text(stringResource(zoneLabelRes(latestZone)), style = MaterialTheme.typography.labelSmall)
            }
          }
        }
        if (windowed.isEmpty()) {
          // Reserves the chart's own space so the card doesn't resize once real data arrives.
          Spacer(Modifier.fillMaxWidth().weight(1f))
        } else {
          val points = remember(windowed, projectedScored) {
            val real = windowed.mapIndexed { index, scored ->
              EffortPoint(
                index.toFloat(),
                scored.source.weight.toFloat(),
                EffortModel.repSize(scored.source.reps),
                scored.zone,
                z = scored.z,
                emphasized = index == windowed.lastIndex
              )
            }
            if (projectedScored == null) {
              real
            } else {
              real + EffortPoint(
                windowed.size.toFloat(),
                projectedScored.source.weight.toFloat(),
                EffortModel.repSize(projectedScored.source.reps),
                projectedScored.zone,
                z = projectedScored.z,
                projected = true
              )
            }
          }
          // A faint dashed rule wherever the window crosses into a new session, so "these 3 are
          // today, that one's from last time" reads at a glance instead of needing the header's
          // single zone label to carry it.
          val sessionGapMarks = remember(windowed) {
            windowed.zipWithNext().mapIndexedNotNull { index, (a, b) ->
              if (a.sessionIndex != b.sessionIndex) index + 0.5f else null
            }
          }
          // The funnel background, driven off each set's own expectation (the capacity value
          // it was scored against) rather than expectedWeight - same source, converted at a
          // literal heavy (4-rep) target and a light target instead of the fit's typical reps.
          // The light target is normally 15 reps but drifts down toward the actually-repeated
          // rep count (scored.lowAnchorReps) while weight and reps hold flat session over
          // session, squeezing the band's floor without moving the 4-rep target.
          // Always one band sample per set - the funnel is never collapsed to a single
          // per-session point, so a mature (SESSION-sourced) stretch and the live BOOTSTRAP
          // stretch render with the same per-set column spacing and the gradient reads
          // continuously across the boundary between them instead of stepping.
          //
          // A projection extends each band by one more sample built from projectedScored's own
          // expectation, which per scoreWithBootstrap's causality is derived only from merged -
          // so this is a real extrapolation of the funnel to the projected point's x, not a
          // shape the projection itself could move.
          //
          // leadIn supplies up to EXTRA_LEADING_POINTS real sets before windowed, x-shifted
          // negative so windowed's own first entry still lands at x=0 - the band's leading edge
          // is then real, causally-scored data continuing off-screen rather than a shape that
          // appears to start from nothing at the window's edge. Never plotted as bubbles (only
          // windowed feeds `points` above) and never used to bootstrap-fill a bandOrigin fade -
          // when leadIn can't reach far enough back itself, EffortChart falls back to bandOrigin.
          val bandTop = remember(leadIn, windowed, projectedScored) {
            buildTrendRuns(
              (leadIn + windowed).mapIndexed { index, scored ->
                (index - leadIn.size).toFloat() to scored.expectation?.let {
                  EffortModel.weightForReps(it, 4)
                }
              } + listOfNotNull(
                projectedScored?.let {
                  windowed.size.toFloat() to it.expectation?.let { e -> EffortModel.weightForReps(e, 4) }
                }
              )
            )
          }
          val bandBottom = remember(leadIn, windowed, projectedScored) {
            buildTrendRuns(
              (leadIn + windowed).mapIndexed { index, scored ->
                (index - leadIn.size).toFloat() to scored.expectation?.let {
                  EffortModel.weightForReps(it, scored.lowAnchorReps)
                }
              } + listOfNotNull(
                projectedScored?.let {
                  windowed.size.toFloat() to it.expectation?.let { e ->
                    EffortModel.weightForReps(e, it.lowAnchorReps)
                  }
                }
              )
            )
          }
          // Left labels are the actually-observed extremes; right labels are the funnel's own
          // extremes (heaviest 4-rep target, lightest low-anchor target) across the window - two
          // independent y-readings rather than merging them into one crowded axis. When the two
          // sides would land close enough to collide (a tight band hugging the data), the
          // observed pair collapses to a single median point instead of fighting the gradient
          // labels for the same pixels - the gradient extremes win since they're what makes the
          // funnel's shape legible, and the median still says roughly where the actual sets sat.
          // Excludes leadIn's negative-x samples (bandTop/bandBottom now reach past x=0 into
          // off-screen real data) - these labels describe the visible window, not data the user
          // can't see.
          val gradientMax = remember(bandTop) {
            bandTop.flatten().filter { it.first >= 0f }.maxOfOrNull { it.second }
          }
          val gradientMin = remember(bandBottom) {
            bandBottom.flatten().filter { it.first >= 0f }.minOfOrNull { it.second }
          }
          val observedMin = remember(points) { points.minOf { it.weight } }
          val observedMax = remember(points) { points.maxOf { it.weight } }
          val overlaps = remember(observedMin, observedMax, gradientMin, gradientMax) {
            val domainMin = minOf(observedMin, gradientMin ?: observedMin)
            val domainMax = maxOf(observedMax, gradientMax ?: observedMax)
            val threshold = (domainMax - domainMin).coerceAtLeast(1f) * 0.12f
            (gradientMax != null && abs(observedMax - gradientMax) < threshold) ||
              (gradientMin != null && abs(observedMin - gradientMin) < threshold)
          }
          val yLabels = remember(points, observedMin, observedMax, overlaps) {
            if (overlaps) {
              val sortedWeights = points.map { it.weight }.sorted()
              val mid = sortedWeights.size / 2
              val median = if (sortedWeights.size % 2 == 0) {
                (sortedWeights[mid - 1] + sortedWeights[mid]) / 2f
              } else {
                sortedWeights[mid]
              }
              listOf(median to median.roundToInt().toString())
            } else {
              listOf(observedMin, observedMax).distinct().map { it to it.roundToInt().toString() }
            }
          }
          val yLabelsRight = remember(gradientMin, gradientMax) {
            listOfNotNull(gradientMin, gradientMax).distinct().map { it to it.roundToInt().toString() }
          }
          // Positions the gradient's midline stop only, so it tracks the same combined domain as
          // the band itself. Built per-set and extended by leadIn exactly like bandTop/bandBottom
          // above - EffortChart requires bandMid's run shape to match theirs (same x-samples) or
          // it falls back to a flat fill.
          val bandMid = remember(leadIn, windowed, projectedScored) {
            buildTrendRuns(
              (leadIn + windowed).mapIndexed { index, scored ->
                (index - leadIn.size).toFloat() to scored.expectedWeight
              } + listOfNotNull(
                projectedScored?.let { windowed.size.toFloat() to it.expectedWeight }
              )
            )
          }
          EffortChart(
            Modifier
              .fillMaxWidth()
              .weight(1f),
            points = points,
            bandTop = bandTop,
            bandBottom = bandBottom,
            bandMid = bandMid,
            bandOrigin = bandOrigin,
            yLabels = yLabels,
            yLabelsRight = yLabelsRight,
            gapMarks = sessionGapMarks,
            compact = true,
            showBandVertices = showBandVertices
          )
        }
      }
    }
  }
}

private fun previewSets(
  daysAgo: List<Long>,
  setsPerDay: Int,
  weight: (daysAgo: Long) -> Double = { 95.0 + it },
  reps: Int = 8,
  restSeconds: Long = 120L
): List<SetRecord> {
  val base = Instant.now().minus(Duration.ofDays(daysAgo.max()))
  return daysAgo.flatMap { day ->
    (0 until setsPerDay).map { setIndex ->
      SetRecord(
        weight = weight(day),
        reps = reps,
        workout = exampleExerciseSet.workout,
        targetSet = exampleExerciseSet.id,
        completed = base.plus(Duration.ofDays(daysAgo.max() - day))
          .plusSeconds(setIndex * restSeconds),
        exercise = exampleExerciseSet.exerciseName
      )
    }
  }
}

/** A first-ever session: every dot COLD regardless of set count, countdown caption shown. */
@Preview
@Composable
private fun PreviewSetTrendStripFirstSession() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(daysAgo = listOf(0L), setsPerDay = 5).map { it.toEffortSet() }
  )
}

/** A second session, first had 3+ sets: bootstrap-fit funnel, dots colored immediately. */
@Preview
@Composable
private fun PreviewSetTrendStripBootstrap() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(daysAgo = listOf(7L, 0L), setsPerDay = 3).map { it.toEffortSet() }
  )
}

/** Long real history: session-fit funnel. */
@Preview
@Composable
private fun PreviewSetTrendStripLongHistory() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(daysAgo = (0L..9L).map { it * 3 }.reversed(), setsPerDay = 3)
      .map { it.toEffortSet() }
  )
}

/** A hypothetical next set (heavier, fewer reps than the funnel supports) previewed past a real
 * history - dashed and dimmer than the solid dots, with the funnel visibly extending to it. */
@Preview
@Composable
private fun PreviewSetTrendStripProjected() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(daysAgo = (0L..9L).map { it * 3 }.reversed(), setsPerDay = 3)
      .map { it.toEffortSet() },
    projected = EffortSet(Instant.now(), weight = 125.0, reps = 4)
  )
}

/** Back after four months at 75% of the old weight: the cooled-down curve reads this on-curve. */
@Preview
@Composable
private fun PreviewSetTrendStripComeback() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(
      daysAgo = listOf(141L, 134L, 127L, 120L, 0L),
      setsPerDay = 3,
      weight = { if (it == 0L) 75.0 else 100.0 }
    ).map { it.toEffortSet() }
  )
}

/** Bodyweight, 30s between sets - compare against the sparse preview below. */
@Preview
@Composable
private fun PreviewSetTrendStripBodyweightDense() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(
      daysAgo = (0L..5L).map { it * 3 }.reversed(),
      setsPerDay = 3,
      weight = { 0.0 },
      reps = 12,
      restSeconds = 30L
    ).map { it.toEffortSet() }
  )
}

/** The same bodyweight work spread out: identical weight and reps, smaller bubbles. */
@Preview
@Composable
private fun PreviewSetTrendStripBodyweightSparse() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(
      daysAgo = (0L..5L).map { it * 3 }.reversed(),
      setsPerDay = 3,
      weight = { 0.0 },
      reps = 12,
      restSeconds = 300L
    ).map { it.toEffortSet() }
  )
}
