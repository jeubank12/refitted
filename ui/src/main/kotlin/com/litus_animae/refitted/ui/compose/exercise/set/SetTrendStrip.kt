package com.litus_animae.refitted.ui.compose.exercise.set

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.effort.EffortModel
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.ui.compose.charts.EffortChart
import com.litus_animae.refitted.ui.compose.charts.EffortPoint
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
      val runs = mutableListOf<MutableList<Pair<Float, Float>>>()
      var current: MutableList<Pair<Float, Float>>? = null
      windowed.forEachIndexed { index, scored ->
        val expectedWeight = expectedWeightBySession[scored.sessionIndex]
        if (expectedWeight == null) {
          current = null
        } else {
          val run = current ?: mutableListOf<Pair<Float, Float>>().also {
            current = it
            runs.add(it)
          }
          run.add(index.toFloat() to expectedWeight.toFloat())
        }
      }
      runs
    }

    Card(Modifier.fillMaxSize(), elevation = 2.dp) {
      EffortChart(Modifier.fillMaxSize(), points = points, trend = trend, compact = true)
    }
  }
}
