package com.litus_animae.refitted.compose.exercise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ColumnScope.ExerciseTimer(
  timeLimitMilliseconds: Long?
) {
  val exerciseTimerRunning = rememberSaveable { mutableStateOf(false) }
  val exerciseTimerMillis = rememberSaveable { mutableLongStateOf(0L) }
  val isExerciseTimerRunning by exerciseTimerRunning
  Timer(isExerciseTimerRunning,
    millisToElapse = timeLimitMilliseconds ?: 0,
    countDown = true,
    animateTimer = false,
    onUpdate = { exerciseTimerMillis.longValue = it }) { exerciseTimerRunning.value = false }
  AnimatedVisibility(
    timeLimitMilliseconds != null,
    enter = expandVertically(expandFrom = Alignment.Top),
    exit = shrinkVertically(shrinkTowards = Alignment.Top)
  ) {
    val displayedTimeLimit = remember { mutableLongStateOf(timeLimitMilliseconds ?: 0L) }
    LaunchedEffect(timeLimitMilliseconds) {
      if (timeLimitMilliseconds != null) displayedTimeLimit.longValue = timeLimitMilliseconds
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
      Button(
        onClick = {
          if (!isExerciseTimerRunning) {
            exerciseTimerMillis.longValue = displayedTimeLimit.longValue
          }
          exerciseTimerRunning.value = !isExerciseTimerRunning
        },
        Modifier.fillMaxWidth()
      ) {
        val timerValue = Instant.ofEpochMilli(exerciseTimerMillis.longValue)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        val timerMax = Instant.ofEpochMilli(displayedTimeLimit.longValue)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        val buttonContent =
          if (isExerciseTimerRunning) "$timerValue remaining (click to stop)"
          else "Start $timerMax exercise timer"
        Text(buttonContent, style = MaterialTheme.typography.h5)
      }
    }
  }
}