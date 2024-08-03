package com.litus_animae.refitted.compose.exercise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.min

@Composable
fun ColumnScope.ExerciseTimer(
  timeLimitMilliseconds: Int?
) {
  val exerciseTimerRunning = rememberSaveable { mutableStateOf(false) }
  val exerciseTimerStart = rememberSaveable { mutableStateOf(Instant.now()) }
  val exerciseTimerDuration = rememberSaveable { mutableIntStateOf(timeLimitMilliseconds ?: 0) }

  val exerciseTimerMillis = remember {
    Animatable(
      if (exerciseTimerRunning.value)
        min(
          exerciseTimerDuration.intValue.toLong(),
          exerciseTimerStart.value.toEpochMilli() + exerciseTimerDuration.intValue - Instant.now()
            .toEpochMilli()
        ).toFloat()
      else exerciseTimerDuration.intValue.toFloat()
    )
  }
  LaunchedEffect(exerciseTimerRunning.value) {
    animateTimer(
      exerciseTimerRunning.value,
      exerciseTimerDuration.intValue,
      exerciseTimerStart.value,
      exerciseTimerMillis,
      whilePausedAnimationSpec = snap(),
    )
  }

  val isExerciseTimerRunning by exerciseTimerRunning
  Timer(
    exerciseTimerStart.value,
    isExerciseTimerRunning,
    durationMillis = timeLimitMilliseconds ?: 0,
    countDown = true,
    animateTimer = false
  ) {
    exerciseTimerRunning.value = false
    exerciseTimerDuration.intValue = timeLimitMilliseconds ?: 0
  }
  AnimatedVisibility(
    timeLimitMilliseconds != null,
    enter = expandVertically(expandFrom = Alignment.Top),
    exit = shrinkVertically(shrinkTowards = Alignment.Top)
  ) {
    val displayedTimeLimit = remember { mutableIntStateOf(timeLimitMilliseconds ?: 0) }
    LaunchedEffect(timeLimitMilliseconds) {
      if (timeLimitMilliseconds != null) displayedTimeLimit.intValue = timeLimitMilliseconds
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
      Button(
        onClick = {
          if (!isExerciseTimerRunning) {
            exerciseTimerDuration.intValue = displayedTimeLimit.intValue
          }
          exerciseTimerRunning.value = !isExerciseTimerRunning
          exerciseTimerStart.value = Instant.now()
        },
        Modifier.fillMaxWidth()
      ) {
        val runningValue by remember { derivedStateOf { floor(exerciseTimerMillis.value / 1000f).toLong() * 1000 }}
        val timerValue = Instant.ofEpochMilli(displayedTimeLimit.intValue.toLong() - runningValue)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        val timerMax = Instant.ofEpochMilli(displayedTimeLimit.intValue.toLong())
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