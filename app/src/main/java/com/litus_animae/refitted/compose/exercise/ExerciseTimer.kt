package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
  if (timeLimitMilliseconds != null) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
      Button(
        onClick = {
          if (!isExerciseTimerRunning) {
            exerciseTimerMillis.longValue = timeLimitMilliseconds
          }
          exerciseTimerRunning.value = !isExerciseTimerRunning
        },
        Modifier.fillMaxWidth()
      ) {
        val timerValue = Instant.ofEpochMilli(exerciseTimerMillis.longValue)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        val timerMax = Instant.ofEpochMilli(timeLimitMilliseconds)
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