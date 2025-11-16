package com.litus_animae.refitted.compose.exercise.set

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.exercise.Timer
import com.litus_animae.refitted.compose.exercise.animateTimer
import com.litus_animae.refitted.models.ExerciseSet
import java.time.Instant
import kotlin.math.min


@Composable
fun RestTimer(
  modifier: Modifier = Modifier,
  isRunning: Boolean,
  durationMillis: Int,
  start: Instant,
  timerRunning: MutableState<Boolean>,
  exerciseSet: ExerciseSet,
  onFinish: () -> Unit
) {
  Column(
    modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val timerElapsed = remember {
      Animatable(
        if (isRunning)
          min(
            durationMillis.toLong(),
            start.toEpochMilli() + durationMillis - Instant.now().toEpochMilli()
          ).toFloat()
        else durationMillis.toFloat()
      )
    }
    LaunchedEffect(timerRunning.value) {
      animateTimer(
        isRunning,
        durationMillis,
        start,
        timerElapsed,
        whilePausedAnimationSpec = snap(),
        pausedTarget = durationMillis
      )
    }
    val localRestFormat = stringResource(R.string.seconds_rest_phrase)
    val runningDisplayTime by remember {
      derivedStateOf {
        String.format(localRestFormat, timerElapsed.value / 1000)
      }
    }
    val timerDisplayTime =
      if (isRunning) runningDisplayTime
      else String.format(localRestFormat, exerciseSet.rest.toFloat())
    Text(timerDisplayTime, style = MaterialTheme.typography.h4)
    AnimatedVisibility(isRunning || exerciseSet.rest > 0) {
      Timer(
        start,
        isRunning,
        durationMillis,
        countDown = true,
        onFinish = onFinish
      )
    }
  }
}
