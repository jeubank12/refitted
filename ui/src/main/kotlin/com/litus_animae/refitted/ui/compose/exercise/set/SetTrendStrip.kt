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
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import com.litus_animae.refitted.data.effort.ExpectationSource
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.charts.EffortChart
import com.litus_animae.refitted.ui.compose.charts.EffortPoint
import com.litus_animae.refitted.ui.compose.charts.buildTrendRuns
import com.litus_animae.refitted.ui.compose.charts.zoneColor
import com.litus_animae.refitted.ui.compose.charts.zoneLabelRes
import com.litus_animae.refitted.ui.compose.exercise.exampleExerciseSet
import com.litus_animae.refitted.ui.compose.util.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val MinSlotWidth = 20.dp
private const val MIN_WINDOW = 5
private const val MAX_WINDOW = 14

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
 */
@Composable
fun SetTrendStrip(
  modifier: Modifier = Modifier,
  merged: List<EffortSet>,
  onClick: () -> Unit = {}
) {
  BoxWithConstraints(modifier) {
    val window = if (maxWidth != Dp.Infinity) {
      (((maxWidth - 12.dp).value / MinSlotWidth.value).toInt()).coerceIn(MIN_WINDOW, MAX_WINDOW)
    } else {
      MIN_WINDOW
    }

    if (merged.isEmpty()) return@BoxWithConstraints

    // scoreWithBootstrap augments score()'s output rather than replacing it - every session
    // past EffortConfig.minPriorSessions behaves identically, and a first-ever session stays
    // all-COLD regardless of its own set count (no prior session exists yet to bootstrap
    // from).
    val series = remember(merged) { EffortModel.scoreWithBootstrap(merged) }
    val windowed = remember(series, window) { series.sets.takeLast(window) }
    if (windowed.isEmpty()) return@BoxWithConstraints

    val points = remember(windowed) {
      windowed.mapIndexed { index, scored ->
        EffortPoint(
          index.toFloat(),
          scored.source.weight.toFloat(),
          scored.size,
          scored.zone,
          emphasized = index == windowed.lastIndex
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
    val yLabels = remember(points) {
      val minWeight = points.minOf { it.weight }
      val maxWeight = points.maxOf { it.weight }
      listOf(minWeight, maxWeight).distinct().map { it to it.roundToInt().toString() }
    }
    // Split by source rather than text-labeling the two - EffortChart draws dashedTrend
    // beneath trend, so the dash itself is the only signal a segment is the coarser,
    // strip-only stand-in rather than the real session-based fit.
    //
    // Built from each set's own expectedWeight rather than a per-session lookup: the bootstrap
    // fit runs at set granularity, so its expectation genuinely moves set to set, and keying
    // off sessionIndex would flatten that into one step per day.
    val realTrend = remember(windowed) {
      buildTrendRuns(
        windowed.mapIndexed { index, scored ->
          index.toFloat() to scored.expectedWeight.takeIf {
            scored.expectationSource == ExpectationSource.SESSION
          }
        }
      )
    }
    val bootstrapTrend = remember(windowed) {
      buildTrendRuns(
        windowed.mapIndexed { index, scored ->
          index.toFloat() to scored.expectedWeight.takeIf {
            scored.expectationSource == ExpectationSource.BOOTSTRAP
          }
        }
      )
    }

    Card(Modifier.fillMaxSize().clickable(onClick = onClick), elevation = 2.dp) {
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
          Text(stringResource(R.string.effort_label), style = MaterialTheme.typography.caption)
          val latestZone = windowed.last().zone
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
            if (latestZone == EffortZone.COLD) {
              // Only reachable during a first-ever session - even one prior session with
              // enough sets unlocks the bootstrap trend immediately, so this is a real state
              // worth naming rather than an unexplained "New" label.
              Text(
                stringResource(R.string.strip_trend_locked),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
              )
            } else {
              Box(
                Modifier
                  .size(8.dp)
                  .background(
                    zoneColor(
                      latestZone,
                      MaterialTheme.colors.primary,
                      Theme.goodAttention,
                      Theme.timerAmber,
                      MaterialTheme.colors.onSurface.copy(alpha = 0.25f)
                    ),
                    CircleShape
                  )
              )
              Spacer(Modifier.width(4.dp))
              Text(stringResource(zoneLabelRes(latestZone)), style = MaterialTheme.typography.caption)
            }
          }
        }
        EffortChart(
          Modifier
            .fillMaxWidth()
            .weight(1f),
          points = points,
          trend = realTrend,
          dashedTrend = bootstrapTrend,
          yLabels = yLabels,
          gapMarks = sessionGapMarks,
          compact = true
        )
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

/** A second session, first had 3+ sets: bootstrap trend dashed, dots colored immediately. */
@Preview
@Composable
private fun PreviewSetTrendStripBootstrap() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(daysAgo = listOf(7L, 0L), setsPerDay = 3).map { it.toEffortSet() }
  )
}

/** Long real history: solid trend, no dash. */
@Preview
@Composable
private fun PreviewSetTrendStripRealTrend() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    merged = previewSets(daysAgo = (0L..9L).map { it * 3 }.reversed(), setsPerDay = 3)
      .map { it.toEffortSet() }
  )
}

/** Back after four months at 75% of the old weight: the cooled-down curve reads this on-curve. */
@Preview
@Composable
private fun PreviewSetTrendStripComeback() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    history = flowOf(
      previewSets(
        daysAgo = listOf(141L, 134L, 127L, 120L, 0L),
        setsPerDay = 3,
        weight = { if (it == 0L) 75.0 else 100.0 }
      )
    ),
    todaysRecords = emptyList()
  )
}

/** Bodyweight, 30s between sets - compare against the sparse preview below. */
@Preview
@Composable
private fun PreviewSetTrendStripBodyweightDense() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    history = flowOf(
      previewSets(
        daysAgo = (0L..5L).map { it * 3 }.reversed(),
        setsPerDay = 3,
        weight = { 0.0 },
        reps = 12,
        restSeconds = 30L
      )
    ),
    todaysRecords = emptyList()
  )
}

/** The same bodyweight work spread out: identical weight and reps, smaller bubbles. */
@Preview
@Composable
private fun PreviewSetTrendStripBodyweightSparse() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    history = flowOf(
      previewSets(
        daysAgo = (0L..5L).map { it * 3 }.reversed(),
        setsPerDay = 3,
        weight = { 0.0 },
        reps = 12,
        restSeconds = 300L
      )
    ),
    todaysRecords = emptyList()
  )
}
