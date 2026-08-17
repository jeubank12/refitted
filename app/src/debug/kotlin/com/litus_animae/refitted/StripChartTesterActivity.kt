package com.litus_animae.refitted

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.ui.compose.exercise.exampleExerciseSet
import com.litus_animae.refitted.ui.compose.exercise.set.SetTrendStrip
import com.litus_animae.refitted.ui.compose.util.Theme
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private const val TODAY_SET_COUNT = 2

/**
 * Manual harness for [SetTrendStrip]'s windowing/session-collapse behavior, which is driven by
 * the strip's actual measured width (see [SetTrendStrip]'s `BoxWithConstraints`) and can't be
 * exercised meaningfully from a fixed-width `@Preview` - it needs a real device screen. Debug-only
 * (registered in app/src/debug/AndroidManifest.xml), so it never reaches a release build.
 */
class StripChartTesterActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme(colors = Theme.lightColors) {
        StripChartTesterScreen()
      }
    }
  }
}

@Composable
private fun StripChartTesterScreen() {
  var historicalCount by remember { mutableFloatStateOf(9f) }
  val total = historicalCount.roundToInt()
  val previousSessionCount = total - TODAY_SET_COUNT

  val merged = remember(total) {
    val previousSession = previewHistorySets(count = previousSessionCount, daysAgo = 3, weight = 95.0)
    val todaysSession = previewHistorySets(count = TODAY_SET_COUNT, daysAgo = 0, weight = 100.0)
    (previousSession + todaysSession).map { it.toEffortSet() }
  }

  Column(
    Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Strip chart tester", style = MaterialTheme.typography.h6)
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
  }
}

private fun previewHistorySets(count: Int, daysAgo: Long, weight: Double): List<SetRecord> {
  val completedBase = Instant.now().minus(Duration.ofDays(daysAgo))
  return (0 until count).map { index ->
    SetRecord(
      weight = weight,
      reps = 8,
      workout = exampleExerciseSet.workout,
      targetSet = exampleExerciseSet.id,
      completed = completedBase.plusSeconds(index * 120L),
      exercise = exampleExerciseSet.exerciseName
    )
  }
}
