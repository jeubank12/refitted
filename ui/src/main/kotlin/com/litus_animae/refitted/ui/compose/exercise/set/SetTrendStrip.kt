package com.litus_animae.refitted.ui.compose.exercise.set

import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.effort.EffortModel
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
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val MinSlotWidth = 20.dp
private const val MIN_WINDOW = 5
private const val MAX_WINDOW = 14

/**
 * Compact effort trend for the most recent sets, sized to however many bubbles actually fit
 * legibly in [modifier]'s measured width rather than a fixed count.
 *
 * [history] is the bounded recentSets flow; [todaysRecords] are the in-memory sets completed
 * this session so a just-finished set appears immediately instead of waiting on Room's
 * write/invalidation round-trip to reach [history].
 */
@Composable
fun SetTrendStrip(
  modifier: Modifier = Modifier,
  history: Flow<List<SetRecord>>,
  todaysRecords: List<Record>
) {
  val recorded by history.collectAsState(initial = emptyList(), Dispatchers.IO)

  // Record.completed on an in-memory set can be stale (copied from the last stored record,
  // possibly a prior day) while the SetRecord Room eventually persists stamps Instant.now() -
  // so today's sets are matched by position against however many of today's history rows have
  // already round-tripped back through recentSets, never by comparing timestamps.
  val merged = remember(recorded, todaysRecords) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val historyToday = recorded.count { it.completed.atZone(zone).toLocalDate() == today }
    val unseen = if (todaysRecords.size > historyToday) {
      todaysRecords.drop(historyToday).map { it.toEffortSet().copy(completed = Instant.now()) }
    } else {
      emptyList()
    }
    recorded.map { it.toEffortSet() } + unseen
  }

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
    // from). See docs/exercise-history-chart.md "Bootstrap trend (strip-only)".
    val series = remember(merged) { EffortModel.scoreWithBootstrap(merged) }
    val windowed = remember(series, window) { series.sets.takeLast(window) }
    if (windowed.isEmpty()) return@BoxWithConstraints

    val points = remember(windowed) {
      windowed.mapIndexed { index, scored ->
        EffortPoint(index.toFloat(), scored.source.weight.toFloat(), scored.size, scored.zone)
      }
    }
    // Split by source rather than text-labeling the two - EffortChart draws dashedTrend
    // beneath trend, so the dash itself is the only signal a segment is the coarser,
    // strip-only stand-in rather than the real session-based fit.
    val realTrend = remember(windowed, series) {
      val expectedWeightBySession = series.trend
        .filter { it.expectationSource == ExpectationSource.SESSION }
        .associateBy({ it.sessionIndex }, { it.expectedWeight })
      buildTrendRuns(
        windowed.mapIndexed { index, scored -> index.toFloat() to scored.sessionIndex },
        expectedWeightBySession
      )
    }
    val bootstrapTrend = remember(windowed, series) {
      val expectedWeightBySession = series.trend
        .filter { it.expectationSource == ExpectationSource.BOOTSTRAP }
        .associateBy({ it.sessionIndex }, { it.expectedWeight })
      buildTrendRuns(
        windowed.mapIndexed { index, scored -> index.toFloat() to scored.sessionIndex },
        expectedWeightBySession
      )
    }

    Card(Modifier.fillMaxSize(), elevation = 2.dp) {
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
          compact = true
        )
      }
    }
  }
}

private fun previewSets(daysAgo: List<Long>, setsPerDay: Int): List<SetRecord> {
  val base = Instant.now().minus(Duration.ofDays(daysAgo.max()))
  return daysAgo.flatMap { day ->
    (0 until setsPerDay).map { setIndex ->
      SetRecord(
        weight = 95.0 + day,
        reps = 8,
        workout = exampleExerciseSet.workout,
        targetSet = exampleExerciseSet.id,
        completed = base.plus(Duration.ofDays(daysAgo.max() - day)).plusSeconds(setIndex * 120L),
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
    history = flowOf(previewSets(daysAgo = listOf(0L), setsPerDay = 5)),
    todaysRecords = emptyList()
  )
}

/** A second session, first had 3+ sets: bootstrap trend dashed, dots colored immediately. */
@Preview
@Composable
private fun PreviewSetTrendStripBootstrap() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    history = flowOf(previewSets(daysAgo = listOf(7L, 0L), setsPerDay = 3)),
    todaysRecords = emptyList()
  )
}

/** Long real history: solid trend, no dash. */
@Preview
@Composable
private fun PreviewSetTrendStripRealTrend() {
  SetTrendStrip(
    Modifier.width(300.dp).height(88.dp),
    history = flowOf(previewSets(daysAgo = (0L..9L).map { it * 3 }.reversed(), setsPerDay = 3)),
    todaysRecords = emptyList()
  )
}
