package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseInstructions(
  setWithRecord: ExerciseSetWithRecord?,
  modifier: Modifier = Modifier
) {
  Card(modifier) {
    Column {
      CompositionLocalProvider(
        LocalOverscrollConfiguration provides null
      ) {
        ExerciseInstructions(setWithRecord)
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.ExerciseInstructions(
  setWithRecord: ExerciseSetWithRecord?
) {
  val exerciseSet = setWithRecord?.exerciseSet
  Row(
    Modifier.weight(5f, fill = true)
  ) {
    // TODO is there a way to show the scrollbar to indicate scrollability?
    // not yet, not implemented by the team -- possibly could use lazy state: https://developer.android.com/jetpack/compose/lists#react-to-scroll-position
    LazyColumn(
      // TODO maybe a fade out in the bottom?
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 5.dp)
    ) {
      stickyHeader {
        Surface(
          Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)) {
          Text(text = exerciseSet?.exerciseName ?: "", style = MaterialTheme.typography.h4)
        }
      }
      item {
        if (exerciseSet != null) {
          val exercise by exerciseSet.exercise.collectAsState(null, Dispatchers.IO)
          Text(exercise?.description ?: "", Modifier.padding(bottom = 5.dp))
        }
      }
      item {
        Text(exerciseSet?.note ?: "")
      }
    }
  }
  // TODO this takes up space if not in use, looks bad with cards
  val exerciseTimerRunning = rememberSaveable { mutableStateOf(false) }
  val exerciseTimerMillis = rememberSaveable { mutableStateOf(0L) }
  val isExerciseTimerRunning by exerciseTimerRunning
  Timer(isExerciseTimerRunning,
    millisToElapse = exerciseSet?.timeLimitMilliseconds ?: 0,
    countDown = true,
    animateTimer = false,
    onUpdate = { exerciseTimerMillis.value = it }) { exerciseTimerRunning.value = false }
  if (exerciseSet?.timeLimitMilliseconds != null) {
    Row(Modifier.weight(1f)) {
      Button(
        onClick = {
          if (!isExerciseTimerRunning) {
            exerciseTimerMillis.value = exerciseSet.timeLimitMilliseconds
          }
          exerciseTimerRunning.value = !isExerciseTimerRunning
        },
        Modifier.fillMaxWidth()
      ) {
        val timerValue = Instant.ofEpochMilli(exerciseTimerMillis.value)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        val timerMax = Instant.ofEpochMilli(exerciseSet.timeLimitMilliseconds)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        if (isExerciseTimerRunning) Text("$timerValue remaining (click to stop)")
        else Text("Start $timerMax exercise timer")
      }
    }
  }
}