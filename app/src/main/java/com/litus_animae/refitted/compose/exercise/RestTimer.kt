package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.compose.util.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun Timer(
  running: Boolean,
  millisToElapse: Long,
  resolutionMillis: Long = 100,
  countDown: Boolean = false,
  debugView: Boolean = false,
  animateTimer: Boolean = true,
  onUpdate: (Long) -> Unit = {},
  onFinish: () -> Unit = {}
) {
  val startTime = rememberSaveable(running) {
    Instant.now()
  }
  val runtimeMillis = rememberSaveable(running) {
    millisToElapse
  }
  var isRunning by rememberSaveable(running) { mutableStateOf(running) }
  val timerScope = rememberCoroutineScope()
  var elapsedMillis by rememberSaveable(running) { mutableStateOf(0L) }
  val millisToShow = if (isRunning) runtimeMillis else millisToElapse
  LaunchedEffect(running, elapsedMillis) {
    if (isRunning) {
      timerScope.launch {
        delay(resolutionMillis)
        val nowElapsed = Instant.now().toEpochMilli() - startTime.toEpochMilli()
        if (nowElapsed > runtimeMillis) {
          isRunning = false
          elapsedMillis = runtimeMillis
          onFinish()
        } else {
          elapsedMillis = nowElapsed
        }
        onUpdate(if (countDown) runtimeMillis - elapsedMillis else elapsedMillis)
      }
    }
  }
  if (animateTimer) {
    if (debugView) {
      Column {
        drawTimer(millisToShow, elapsedMillis, countDown)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Running: $running")
          Text("IsRunning: $isRunning")
          Text("millisToElapse: $runtimeMillis")
          Text("elapsedMillis: $elapsedMillis")
        }
      }
    } else {
      drawTimer(millisToShow, elapsedMillis, countDown)
    }
  }
}

@Composable
private fun drawTimer(millisToElapse: Long, elapsedMillis: Long, countDown: Boolean) {
  val drawColor = MaterialTheme.colors.onSurface
  val elapsedColor = MaterialTheme.colors.primary
  Canvas(
    Modifier
      .fillMaxWidth()
      .height(10.dp)
      .background(MaterialTheme.colors.surface)
  ) {
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
        strokeWidth = 10f
      )
    }
    drawLine(
      drawColor,
      start = elapsedOffset,
      end = endOffset,
      strokeWidth = 10f
    )
  }
}

class ElapsedMillisParameterProvider : PreviewParameterProvider<Long> {
  override val values: Sequence<Long> = sequenceOf(
    0L, 10000L, 30000L, 59000L, 60000L
  )
}

@Composable
@Preview(widthDp = 800)
fun PreviewTimer(@PreviewParameter(ElapsedMillisParameterProvider::class) elapsedMillis: Long) {
  MaterialTheme(Theme.lightColors) {
    drawTimer(60000L, elapsedMillis, countDown = false)
  }
}

@Composable
@Preview(widthDp = 800)
fun PreviewRunningTimer() {
  var running by remember { mutableStateOf(false) }
  var down by remember { mutableStateOf(false) }
  MaterialTheme(Theme.lightColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Timer(running, 15000L, debugView = true, countDown = down) { running = false }
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