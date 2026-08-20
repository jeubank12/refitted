package com.litus_animae.refitted

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.ui.compose.exercise.exampleExerciseSet
import com.litus_animae.refitted.ui.compose.exercise.set.SetTrendStrip
import com.litus_animae.refitted.ui.compose.util.RefittedTheme
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private const val TODAY_SET_COUNT = 2
private const val DEFAULT_TOTAL = 9
private const val PREVIOUS_SESSION_DAYS_AGO = 3L
private const val DEFAULT_PREVIOUS_WEIGHT = 95.0
private const val DEFAULT_TODAY_WEIGHT = 100.0
private const val DEFAULT_REPS = 8

/**
 * Manual harness for [SetTrendStrip]'s windowing/session-collapse behavior, which is driven by
 * the strip's actual measured width (see [SetTrendStrip]'s `BoxWithConstraints`) and can't be
 * exercised meaningfully from a fixed-width `@Preview` - it needs a real device screen. Lets
 * each historical set's own weight/reps be edited in place (not just how many there are), so a
 * single set's contribution to the strip's zone coloring and trend fit can be isolated.
 * Debug-only (registered in app/src/debug/AndroidManifest.xml), so it never reaches a release
 * build.
 */
class StripChartTesterActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RefittedTheme {
        StripChartTesterScreen()
      }
    }
  }
}

/**
 * One historical set's editable weight/reps, backed by its own snapshot state so a single row's
 * edit doesn't recompose the whole [rows] list.
 */
private class EditableSet(initialWeight: Double, initialReps: Int) {
  var weight by mutableStateOf(initialWeight)
  var reps by mutableIntStateOf(initialReps)
}

private fun defaultRow(daysAgo: Long) =
  EditableSet(if (daysAgo == 0L) DEFAULT_TODAY_WEIGHT else DEFAULT_PREVIOUS_WEIGHT, DEFAULT_REPS)

/**
 * Grows/shrinks the previous-session portion of [rows] to match [target], preserving every
 * row's already-edited weight/reps - only the count slider adds or removes rows, editing a
 * value never does. The trailing [TODAY_SET_COUNT] rows are always "today" and are never
 * touched by resizing.
 */
private fun resizeRows(rows: SnapshotStateList<EditableSet>, target: Int) {
  while (rows.size < target) {
    val insertIndex = (rows.size - TODAY_SET_COUNT).coerceAtLeast(0)
    rows.add(insertIndex, defaultRow(PREVIOUS_SESSION_DAYS_AGO))
  }
  while (rows.size > target) {
    val removeIndex = (rows.size - TODAY_SET_COUNT - 1).coerceAtLeast(0)
    rows.removeAt(removeIndex)
  }
}

@Composable
private fun StripChartTesterScreen() {
  var historicalCount by remember { mutableFloatStateOf(DEFAULT_TOTAL.toFloat()) }
  val total = historicalCount.roundToInt()

  val rows = remember {
    mutableStateListOf<EditableSet>().apply {
      addAll((0 until DEFAULT_TOTAL - TODAY_SET_COUNT).map { defaultRow(PREVIOUS_SESSION_DAYS_AGO) })
      addAll((0 until TODAY_SET_COUNT).map { defaultRow(0L) })
    }
  }
  resizeRows(rows, total)
  val previousSessionCount = rows.size - TODAY_SET_COUNT

  val merged = remember(rows.map { it.weight to it.reps }) {
    rows.mapIndexed { index, row ->
      val isToday = index >= previousSessionCount
      val daysAgo = if (isToday) 0L else PREVIOUS_SESSION_DAYS_AGO
      val positionInSession = if (isToday) index - previousSessionCount else index
      val completed = Instant.now()
        .minus(Duration.ofDays(daysAgo))
        .plusSeconds(positionInSession * 120L)
      SetRecord(
        weight = row.weight,
        reps = row.reps,
        workout = exampleExerciseSet.workout,
        targetSet = exampleExerciseSet.id,
        completed = completed,
        exercise = exampleExerciseSet.exerciseName
      )
    }.map { it.toEffortSet() }
  }

  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Strip chart tester", style = MaterialTheme.typography.headlineLarge)
    Text(
      "Historical entries: $total " +
        "(previous session: $previousSessionCount, today: $TODAY_SET_COUNT)"
    )
    Slider(
      value = historicalCount,
      onValueChange = { historicalCount = it },
      valueRange = 5f..15f,
      steps = 9
    )
    SetTrendStrip(
      Modifier.fillMaxWidth().height(88.dp),
      merged = merged
    )
    HorizontalDivider()
    Text("Tap +/- to adjust each set's weight and reps", style = MaterialTheme.typography.labelMedium)
    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      items(rows.size) { index ->
        val row = rows[index]
        val label = if (index >= previousSessionCount) {
          "Today set ${index - previousSessionCount + 1}"
        } else {
          "Prev set ${index + 1}"
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
          Stepper(
            label = "wt",
            value = "${row.weight.roundToInt()}",
            onDecrement = { row.weight = (row.weight - 5.0).coerceAtLeast(0.0) },
            onIncrement = { row.weight += 5.0 }
          )
          Stepper(
            label = "reps",
            value = "${row.reps}",
            onDecrement = { row.reps = (row.reps - 1).coerceAtLeast(0) },
            onIncrement = { row.reps += 1 }
          )
        }
      }
    }
  }
}

@Composable
private fun Stepper(label: String, value: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    TextButton(onClick = onDecrement) { Text("-") }
    Text("$label $value", Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = onIncrement) { Text("+") }
  }
}

/**
 * Static layout check only - a fixed-width `@Preview` can't exercise the width-driven
 * windowing/session-collapse behavior this activity exists to test on-device; see the
 * activity's kdoc.
 */
@Preview
@Composable
private fun PreviewStripChartTesterScreen() {
  RefittedTheme {
    StripChartTesterScreen()
  }
}
