package com.litus_animae.refitted.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flow
import java.time.Instant

@Composable
fun Timer(running: Boolean, millisToElapse: Long) {
    val startTime = rememberSaveable(running, Instant::toEpochMilli) {
        Instant.now()
    }
    var isRunning by rememberSaveable { mutableStateOf(running) }
    val timerScope = rememberCoroutineScope()
    val elapsedMillis by flow {
        while (running && isRunning) {
            val elapsed = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            if (elapsed > millisToElapse) {
                isRunning = false
                emit(millisToElapse)
            } else {
                emit(Instant.now().toEpochMilli() - startTime.toEpochMilli())
            }
        }
    }.collectAsState(initial = 0L, timerScope.coroutineContext)
    drawTimer(millisToElapse, elapsedMillis)
}

@Composable
private fun drawTimer(millisToElapse: Long, elapsedMillis: Long) {
    val drawColor = MaterialTheme.colors.onSurface
    val elapsedColor = MaterialTheme.colors.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
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
                strokeWidth = 1f
            )
        }
        drawLine(
            drawColor,
            start = elapsedOffset,
            end = endOffset,
            strokeWidth = 1f
        )
    }
}

class ElapsedMillisParameterProvider : PreviewParameterProvider<Long> {
    override val values: Sequence<Long> = sequenceOf(
        0L, 10L, 30L, 59L, 60L
    )
}

@Composable
@Preview(widthDp = 100)
fun PreviewTimer(@PreviewParameter(ElapsedMillisParameterProvider::class) elapsedMillis: Long) {
    MaterialTheme(Theme.lightColors) {
        drawTimer(60L, elapsedMillis)
    }
}

@Composable
@Preview(widthDp = 100)
fun PreviewRunningTimer() {
    MaterialTheme(Theme.lightColors) {
        Timer(true, 60L)
    }
}