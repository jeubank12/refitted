package com.litus_animae.refitted.ui.compose.exercise.set

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.effort.EffortModel
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.charts.EffortChart
import com.litus_animae.refitted.ui.compose.charts.EffortPoint
import com.litus_animae.refitted.ui.compose.charts.buildTrendRuns
import com.litus_animae.refitted.ui.compose.charts.zoneColor
import com.litus_animae.refitted.ui.compose.charts.zoneLabelRes
import com.litus_animae.refitted.ui.compose.util.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    val series = remember(merged) { EffortModel.score(merged) }
    val windowed = remember(series, window) { series.sets.takeLast(window) }
    if (windowed.isEmpty()) return@BoxWithConstraints

    val points = remember(windowed) {
      windowed.mapIndexed { index, scored ->
        EffortPoint(index.toFloat(), scored.source.weight.toFloat(), scored.size, scored.zone)
      }
    }
    val trend = remember(windowed, series) {
      val expectedWeightBySession = series.trend.associateBy({ it.sessionIndex }, { it.expectedWeight })
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
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
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
            Text(stringResource(zoneLabelRes(latestZone)), style = MaterialTheme.typography.caption)
          }
        }
        EffortChart(
          Modifier
            .fillMaxWidth()
            .weight(1f),
          points = points,
          trend = trend,
          compact = true
        )
      }
    }
  }
}
