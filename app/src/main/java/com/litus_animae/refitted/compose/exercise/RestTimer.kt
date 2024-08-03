package com.litus_animae.refitted.compose.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.compose.util.Theme
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

@Composable
fun Timer(
  start: Instant,
  isRunning: Boolean,
  durationMillis: Int,
  countDown: Boolean = false,
  debugView: Boolean = false,
  animateTimer: Boolean = true,
  onFinish: () -> Unit = {}
) {
  val elapsedMillis = remember {
    Animatable(
      if (isRunning)
        min(
          durationMillis.toFloat(),
          (Instant.now().toEpochMilli() - start.toEpochMilli()).toFloat()
        )
      else durationMillis.toFloat()
    )
  }

  LaunchedEffect(start, isRunning) {
    animateTimer(isRunning, durationMillis, start, elapsedMillis, onFinish = onFinish)
  }

  if (animateTimer) {
    if (debugView) {
      Column {
        DrawTimer(
          Modifier.fillMaxWidth(),
          durationMillis,
          { elapsedMillis.value.toInt() },
          countDown
        )
        val elapsed by remember { derivedStateOf { elapsedMillis.value.toInt() }}
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("el: $elapsed")
          Text("r: $isRunning")
          Text("d: $durationMillis")
        }
      }
    } else {
      DrawTimer(Modifier.fillMaxWidth(), durationMillis, { elapsedMillis.value.toInt() }, countDown)
    }
  }
}

suspend fun animateTimer(
  isRunning: Boolean,
  durationMillis: Int,
  start: Instant,
  elapsedMillis: Animatable<Float, AnimationVector1D>,
  catchupDurationMillis: Int = 100,
  whilePausedAnimationSpec: AnimationSpec<Float> = tween(500),
  pausedTarget: Int = 0,
  onFinish: () -> Unit = {}
) {
  if (isRunning) {
    val alreadyElapsedMillis =
      min(durationMillis, (Instant.now().toEpochMilli() - start.toEpochMilli()).toInt())
    val target = start.toEpochMilli() + durationMillis
    if (alreadyElapsedMillis < durationMillis - catchupDurationMillis) {
      elapsedMillis.animateTo(
        alreadyElapsedMillis + catchupDurationMillis.toFloat(),
        tween(100, easing = LinearEasing)
      )
    }
    val remainingMillis = max(0, (target - Instant.now().toEpochMilli()).toInt())
    elapsedMillis.animateTo(
      durationMillis.toFloat(),
      tween(remainingMillis, easing = LinearEasing)
    )
    onFinish()
  } else {
    elapsedMillis.animateTo(pausedTarget.toFloat(), whilePausedAnimationSpec)
  }
}

@Composable
private fun DrawTimer(
  modifier: Modifier = Modifier,
  millisToElapse: Int,
  elapsedMillisProvider: () -> Int,
  countDown: Boolean
) {
  val drawColor = MaterialTheme.colors.onPrimary
  val elapsedColor = MaterialTheme.colors.primary

  val elapsedMillisR = remember(elapsedMillisProvider) {
    derivedStateOf {
      elapsedMillisProvider()
    }
  }

  Canvas(
    modifier
      .height(20.dp)
      .background(MaterialTheme.colors.surface)
  ) {
    val elapsedMillis by elapsedMillisR
    val offsetMillis = if (countDown) millisToElapse - elapsedMillis else elapsedMillis
    val elapsedOffsetX = (size.width - 6f) / millisToElapse * offsetMillis
    val startOffset = Offset(3f, size.height / 2f)
    val endOffset = Offset(size.width - 3f, size.height / 2f)
    val elapsedOffset = startOffset.plus(Offset(elapsedOffsetX, 0f))
    if (startOffset != elapsedOffset) {
      drawLine(
        elapsedColor,
        start = startOffset,
        end = elapsedOffset,
        strokeWidth = 20f
      )
    }
    drawLine(
      drawColor,
      start = elapsedOffset,
      end = endOffset,
      strokeWidth = 20f
    )
  }
}

class ElapsedMillisParameterProvider : PreviewParameterProvider<Int> {
  override val values: Sequence<Int> = sequenceOf(
    0, 10000, 30000, 59000, 60000
  )
}

@Composable
@Preview(widthDp = 800)
fun PreviewTimer(@PreviewParameter(ElapsedMillisParameterProvider::class) elapsedMillis: Int) {
  MaterialTheme(Theme.lightColors) {
    DrawTimer(Modifier.fillMaxWidth(), 60000, { elapsedMillis }, countDown = false)
  }
}

@Composable
@Preview(widthDp = 800)
fun PreviewRunningTimer() {
  var running by remember { mutableStateOf(false) }
  val start = remember(running) { Instant.now() }
  var down by remember { mutableStateOf(false) }
  MaterialTheme(Theme.lightColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Timer(start, running, 15000, debugView = true, countDown = down)
      Row {
        Button(onClick = { running = !running }) {
          Text(running.toString())
        }
        Button(onClick = { down = !down }) {
          Text(if (down) "down" else "up")
        }
      }
    }
  }
}