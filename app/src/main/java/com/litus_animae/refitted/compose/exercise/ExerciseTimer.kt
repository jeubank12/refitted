package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ColumnScope.ExerciseTimer(
  timeLimitMilliseconds: Long?
) {
  val exerciseTimerRunning = rememberSaveable { mutableStateOf(false) }
  val exerciseTimerMillis = rememberSaveable { mutableStateOf(0L) }
  val isExerciseTimerRunning by exerciseTimerRunning
  Timer(isExerciseTimerRunning,
    millisToElapse = timeLimitMilliseconds ?: 0,
    countDown = true,
    animateTimer = false,
    onUpdate = { exerciseTimerMillis.value = it }) { exerciseTimerRunning.value = false }
  if (timeLimitMilliseconds != null) {
    Row(Modifier.weight(1f)) {
      Button(
        onClick = {
          if (!isExerciseTimerRunning) {
            exerciseTimerMillis.value = timeLimitMilliseconds
          }
          exerciseTimerRunning.value = !isExerciseTimerRunning
        },
        Modifier.fillMaxWidth()
      ) {
        val timerValue = Instant.ofEpochMilli(exerciseTimerMillis.value)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        val timerMax = Instant.ofEpochMilli(timeLimitMilliseconds)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        if (isExerciseTimerRunning) Text("$timerValue remaining (click to stop)")
        else Text("Start $timerMax exercise timer")
      }
    }
  }
}