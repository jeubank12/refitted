package com.litus_animae.refitted.compose

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.Instant

@Composable
fun Timer(running: Boolean, millisToElapse: Long, debugView: Boolean = false) {
    val startTime = rememberSaveable(running, Instant::toEpochMilli) {
        Instant.now()
    }
    var isRunning by rememberSaveable(running) { mutableStateOf(running) }
    val timerScope = rememberCoroutineScope()
    val elapsedMillis by flow {
        while (isRunning) {
            val elapsed = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            if (elapsed > millisToElapse) {
                isRunning = false
                emit(millisToElapse)
            } else {
                emit(Instant.now().toEpochMilli() - startTime.toEpochMilli())
                delay(100)
            }
        }
    }.collectAsState(initial = 0L, timerScope.coroutineContext)
    if (debugView) {
        Column {
            drawTimer(millisToElapse, elapsedMillis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Running: $running")
                Text("IsRunning: $isRunning")
                Text("millisToElapse: $millisToElapse")
                Text("elapsedMillis: ${elapsedMillis.toString(6)}")
            }
        }
    } else {
        drawTimer(millisToElapse, elapsedMillis)
    }
}

@Composable
private fun drawTimer(millisToElapse: Long, elapsedMillis: Long) {
    val drawColor = MaterialTheme.colors.onSurface
    val elapsedColor = MaterialTheme.colors.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colors.surface)
    ) {
        val elapsedOffsetX = (size.width - 6f) / millisToElapse * elapsedMillis
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
        drawTimer(60000L, elapsedMillis)
    }
}

@Composable
@Preview(widthDp = 800)
fun PreviewRunningTimer() {
    var running by remember { mutableStateOf(false) }
    MaterialTheme(Theme.lightColors) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Timer(running, 60000L, debugView = true)
            Button(onClick = { running = !running }) {
                Text(running.toString())
            }
        }
    }
}