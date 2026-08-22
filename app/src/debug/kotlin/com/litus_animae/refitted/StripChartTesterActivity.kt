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
import androidx.compose.material3.Switch
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
import com.litus_animae.refitted.data.effort.EffortSet
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.ui.compose.exercise.exampleExerciseSet
import com.litus_animae.refitted.ui.compose.exercise.set.SetTrendStrip
import com.litus_animae.refitted.ui.compose.util.RefittedTheme
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private const val MIN_SESSIONS = 1
private const val MAX_SESSIONS = 6
private const val DEFAULT_SESSION_COUNT = 3
private const val SESSION_SPACING_DAYS = 3L
private const val MIN_SETS_PER_SESSION = 1
private const val MAX_SETS_PER_SESSION = 8
private const val DEFAULT_SETS_PER_SESSION = 3
private const val DEFAULT_TODAY_WEIGHT = 100.0
private const val DEFAULT_PREVIOUS_WEIGHT = 95.0
private const val DEFAULT_REPS = 8
private const val SET_SPACING_SECONDS = 120L
private const val DEFAULT_PROJECTED_WEIGHT = 105.0
private const val DEFAULT_PROJECTED_REPS = 8

/**
 * Manual harness for [SetTrendStrip]'s windowing/session-collapse behavior, which is driven by
 * the strip's actual measured width (see [SetTrendStrip]'s `BoxWithConstraints`) and can't be
 * exercised meaningfully from a fixed-width `@Preview` - it needs a real device screen. Both the
 * session boundary (how many sessions, how many sets in each) and each individual set's own
 * weight/reps are editable in place, so a single set's contribution to the strip's zone coloring
 * and trend fit can be isolated. A separate weight/reps stepper drives the strip's `projected`
 * point - the same live-preview dot the real exercise screen feeds from its own steppers - so its
 * dashed styling and the funnel's one-step extension to it can be tuned the same way. Debug-only
 * (registered in app/src/debug/AndroidManifest.xml), so it never reaches a release build.
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
 * edit doesn't recompose the whole session list.
 */
private class EditableSet(initialWeight: Double, initialReps: Int) {
  var weight by mutableStateOf(initialWeight)
  var reps by mutableIntStateOf(initialReps)
}

/** One historical session: an ordered, independently resizable list of [EditableSet]s. */
private class EditableSession {
  val sets = mutableStateListOf<EditableSet>()
}

private fun defaultWeight(daysAgo: Long) = if (daysAgo == 0L) DEFAULT_TODAY_WEIGHT else DEFAULT_PREVIOUS_WEIGHT

/** Grows/shrinks [session]'s sets to [target], preserving already-edited rows. */
private fun resizeSets(session: EditableSession, target: Int, daysAgo: Long) {
  while (session.sets.size < target) {
    session.sets.add(EditableSet(defaultWeight(daysAgo), DEFAULT_REPS))
  }
  while (session.sets.size > target) {
    session.sets.removeAt(session.sets.lastIndex)
  }
}

/**
 * Grows/shrinks [sessions] to [target]. New sessions are inserted at the front (older) and
 * removed from the front when shrinking, so the most recent session - "today" - stays the last
 * entry and keeps its own edited sets regardless of how many sessions exist.
 */
private fun resizeSessions(sessions: SnapshotStateList<EditableSession>, target: Int) {
  while (sessions.size < target) {
    sessions.add(0, EditableSession())
  }
  while (sessions.size > target) {
    sessions.removeAt(0)
  }
}

/** Days-ago for the session at [index] out of [sessionCount] total, most recent (today) last. */
private fun sessionDaysAgo(index: Int, sessionCount: Int) = (sessionCount - 1 - index) * SESSION_SPACING_DAYS

@Composable
private fun StripChartTesterScreen() {
  var sessionCountFloat by remember { mutableFloatStateOf(DEFAULT_SESSION_COUNT.toFloat()) }
  val sessionCount = sessionCountFloat.roundToInt()

  val sessions = remember { mutableStateListOf<EditableSession>() }
  resizeSessions(sessions, sessionCount)
  sessions.forEachIndexed { index, session ->
    if (session.sets.isEmpty()) {
      resizeSets(session, DEFAULT_SETS_PER_SESSION, sessionDaysAgo(index, sessionCount))
    }
  }

  val merged = sessions.flatMapIndexed { sessionIndex, session ->
    val daysAgo = sessionDaysAgo(sessionIndex, sessionCount)
    session.sets.mapIndexed { setIndex, row ->
      SetRecord(
        weight = row.weight,
        reps = row.reps,
        workout = exampleExerciseSet.workout,
        targetSet = exampleExerciseSet.id,
        completed = Instant.now().minus(Duration.ofDays(daysAgo)).plusSeconds(setIndex * SET_SPACING_SECONDS),
        exercise = exampleExerciseSet.exerciseName
      )
    }
  }.map { it.toEffortSet() }

  var projectedWeight by remember { mutableStateOf(DEFAULT_PROJECTED_WEIGHT) }
  var projectedReps by remember { mutableIntStateOf(DEFAULT_PROJECTED_REPS) }
  val projectedAt = remember { Instant.now() }
  var showBandVertices by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Strip chart tester", style = MaterialTheme.typography.headlineLarge)
    Text("Sessions: $sessionCount, total sets: ${sessions.sumOf { it.sets.size }}")
    Slider(
      value = sessionCountFloat,
      onValueChange = { sessionCountFloat = it },
      valueRange = MIN_SESSIONS.toFloat()..MAX_SESSIONS.toFloat(),
      steps = MAX_SESSIONS - MIN_SESSIONS - 1
    )
    SetTrendStrip(
      Modifier.fillMaxWidth().height(88.dp),
      merged = merged,
      projected = EffortSet(projectedAt, projectedWeight, projectedReps),
      showBandVertices = showBandVertices
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Show band control points", Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
      Switch(checked = showBandVertices, onCheckedChange = { showBandVertices = it })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Projected next set", Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
      Stepper(
        label = "wt",
        value = "${projectedWeight.roundToInt()}",
        onDecrement = { projectedWeight = (projectedWeight - 5.0).coerceAtLeast(0.0) },
        onIncrement = { projectedWeight += 5.0 }
      )
      Stepper(
        label = "reps",
        value = "$projectedReps",
        onDecrement = { projectedReps = (projectedReps - 1).coerceAtLeast(0) },
        onIncrement = { projectedReps += 1 }
      )
    }
    HorizontalDivider()
    Text(
      "+/- a session's set count, or a set's own weight/reps",
      style = MaterialTheme.typography.labelMedium
    )
    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      sessions.forEachIndexed { sessionIndex, session ->
        val daysAgo = sessionDaysAgo(sessionIndex, sessionCount)
        item {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val sessionLabel = if (daysAgo == 0L) "Today" else "$daysAgo days ago"
            Text(
              sessionLabel,
              Modifier.width(96.dp),
              style = MaterialTheme.typography.titleSmall
            )
            Stepper(
              label = "sets",
              value = "${session.sets.size}",
              onDecrement = {
                resizeSets(session, (session.sets.size - 1).coerceAtLeast(MIN_SETS_PER_SESSION), daysAgo)
              },
              onIncrement = {
                resizeSets(session, (session.sets.size + 1).coerceAtMost(MAX_SETS_PER_SESSION), daysAgo)
              }
            )
          }
        }
        items(session.sets.size) { setIndex ->
          val row = session.sets[setIndex]
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Set ${setIndex + 1}", Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
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
@Preview(showBackground = true, widthDp = 450, heightDp = 1200)
@Composable
private fun PreviewStripChartTesterScreen() {
  RefittedTheme {
    StripChartTesterScreen()
  }
}
