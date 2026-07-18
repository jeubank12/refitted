package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.litus_animae.refitted.ui.compose.util.Theme
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.min

/**
 * A circular rest-timer ring.
 *
 * **Idle** (not resting): ring is filled in the primary colour up to the proportion
 * [restSeconds] / [maxRestSeconds], so rests are visually comparable across exercises
 * in the same workout day. A +/- control lets the user override the rest duration.
 *
 * **Running** (resting): arc depletes clockwise; centre shows remaining seconds; goes
 * amber under 10 s. If [nextRestSeconds] is non-null a secondary note is shown so the
 * user can see what's coming next — the caller decides when passing it is meaningful
 * (e.g. suppressing it once an exercise has no sets left), this composable just renders
 * whatever it's given.
 *
 * Reuses [animateTimer] from `Timer.kt` so animation behaviour is consistent with the
 * existing horizontal bar timer.
 */
@Composable
fun CircularRestTimer(
  restSeconds: Int,
  maxRestSeconds: Int,
  isRunning: Boolean,
  startedAt: Instant,
  modifier: Modifier = Modifier,
  nextRestSeconds: Int? = null,
  onAdjust: ((Int) -> Unit)? = null,
  onFinish: () -> Unit = {}
) {
  val durationMillis = restSeconds * 1000
  val safeMax = maxRestSeconds.coerceAtLeast(1)

  // Recreate the animatable whenever a new timer starts (startedAt changes).
  // When already running on first composition, seek to the elapsed position immediately.
  val elapsedMillisAnimatable = remember(startedAt) {
    Animatable(
      if (isRunning)
        min(
          durationMillis.toFloat(),
          (Instant.now().toEpochMilli() - startedAt.toEpochMilli()).toFloat()
        )
      else 0f
    )
  }

  LaunchedEffect(startedAt, isRunning) {
    animateTimer(
      isRunning = isRunning,
      durationMillis = durationMillis,
      start = startedAt,
      elapsedMillisAnimatable = elapsedMillisAnimatable,
      onFinish = onFinish
    )
  }

  // Keyed on the animatable itself (not just startedAt) so this is rebuilt against the
  // current run's animatable — otherwise it keeps observing the first run's object forever.
  // Ceiling, not floor/truncation: at t=0 the remainder is a hair under durationMillis,
  // and truncating would show e.g. "9s" instead of "10s" for the first instant of a run.
  val remainingSeconds by remember(elapsedMillisAnimatable) {
    derivedStateOf {
      ceil((durationMillis - elapsedMillisAnimatable.value) / 1000f).toInt().coerceAtLeast(0)
    }
  }
  val isAlmostDone = isRunning && remainingSeconds <= 10

  val primaryColor = MaterialTheme.colors.primary
  val amberColor = Theme.timerAmber
  val trackColor = Theme.timerTrack

  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    BoxWithConstraints(
      modifier = Modifier.weight(1f),
      contentAlignment = Alignment.Center
    ) {
      // Constrain the ring to a square so it's always circular
      val ringDp: Dp = min(maxWidth, maxHeight) - 16.dp

      Box(Modifier.size(ringDp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
          val strokeWidth = (size.minDimension * 0.08f).coerceAtLeast(8f)
          val inset = strokeWidth / 2f
          val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
          val topLeft = Offset(inset, inset)
          val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

          // Muted track — full circle
          drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
          )

          // Content arc — counter-clockwise from 12 o'clock (egg-timer style).
          // Both branches scale against safeMax (not durationMillis) so the running arc
          // starts exactly where the idle arc left off and depletes continuously to empty,
          // instead of jumping to a full circle the instant the timer starts.
          // Idle: arc shows how much rest is set vs the day's max.
          // Running: arc depletes clockwise back toward 12 o'clock.
          val sweep = if (isRunning) {
            val remainingMillis = (durationMillis - elapsedMillisAnimatable.value).coerceAtLeast(0f)
            -360f * (remainingMillis / (safeMax * 1000f))  // negative = counter-clockwise
          } else {
            -360f * (restSeconds.toFloat() / safeMax)  // negative = counter-clockwise
          }
          if (sweep != 0f) {
            drawArc(
              color = if (isAlmostDone) amberColor else primaryColor,
              startAngle = -90f,
              sweepAngle = sweep,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = stroke
            )
          }
        }

        // Centre label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          if (isRunning) {
            Text(
              "${remainingSeconds}s",
              style = MaterialTheme.typography.h4,
              color = if (isAlmostDone) amberColor else MaterialTheme.colors.onSurface
            )
          } else {
            Text(
              "${restSeconds}s",
              style = MaterialTheme.typography.h4,
              color = primaryColor
            )
          }
          Text("rest", style = MaterialTheme.typography.caption)
          // Always emitted, idle or running (not conditionally included) so this line's
          // height is reserved at all times — otherwise the centre column shifts both
          // when a rest starts/stops and as nextRestSeconds appears/disappears mid-rest.
          Text(
            text = nextRestSeconds?.let { "next: ${it}s" } ?: "",
            style = MaterialTheme.typography.overline,
            color = MaterialTheme.colors.onSurface.copy(
              alpha = if (isRunning && nextRestSeconds != null) 0.6f else 0f
            )
          )
        }
      }
    }

    // Rest duration +/- controls
    if (onAdjust != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { onAdjust((restSeconds - 5).coerceIn(0, maxRestSeconds)) },
          enabled = !isRunning && restSeconds > 0
        ) {
          Icon(Icons.Default.Remove, contentDescription = "decrease rest")
        }
        Text("${restSeconds}s", style = MaterialTheme.typography.body2)
        IconButton(
          onClick = { onAdjust((restSeconds + 5).coerceIn(0, maxRestSeconds)) },
          enabled = !isRunning && restSeconds < maxRestSeconds
        ) {
          Icon(Icons.Default.Add, contentDescription = "increase rest")
        }
      }
    }
  }
}

private class IdleFillRatioProvider : PreviewParameterProvider<Int> {
  // restSeconds against a fixed 90s maxRestSeconds, to see the idle ring at
  // a few different fill proportions
  override val values: Sequence<Int> = sequenceOf(0, 20, 45, 90)
}

@Composable
@Preview(widthDp = 220, heightDp = 260, apiLevel = 36)
fun PreviewCircularRestTimerIdle(
  @PreviewParameter(IdleFillRatioProvider::class) restSeconds: Int
) {
  MaterialTheme(Theme.lightColors) {
    Card(Modifier.fillMaxSize()) {
      CircularRestTimer(
        restSeconds = restSeconds,
        maxRestSeconds = 90,
        isRunning = false,
        startedAt = Instant.now(),
        onAdjust = {}
      )
    }
  }
}

/**
 * Interactive preview: start/stop the rest, nudge the duration, and watch the arc
 * deplete in real time — a local stand-in for the exercise screen while developing.
 */
@Composable
@Preview(widthDp = 220, heightDp = 300, apiLevel = 36)
fun PreviewCircularRestTimerInteractive() {
  var running by remember { mutableStateOf(false) }
  var restSeconds by remember { mutableIntStateOf(30) }
  val startedAt = remember(running) { Instant.now() }

  MaterialTheme(Theme.lightColors) {
    Card(Modifier.fillMaxSize()) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp)) {
          CircularRestTimer(
            restSeconds = restSeconds,
            maxRestSeconds = 90,
            isRunning = running,
            startedAt = startedAt,
            nextRestSeconds = 60,
            onAdjust = { restSeconds = it },
            onFinish = { running = false }
          )
        }
        Row {
          Button(onClick = { running = !running }) {
            Text(if (running) "stop" else "start")
          }
        }
      }
    }
  }
}
