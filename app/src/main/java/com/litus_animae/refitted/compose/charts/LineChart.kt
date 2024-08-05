package com.litus_animae.refitted.compose.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.litus_animae.refitted.util.rangeOf
import java.time.Instant
import kotlin.math.max

@Composable
fun LineChart(
  modifier: Modifier = Modifier,
  data: List<Pair<Instant, Float>>,
  pointColor: Color = Color.Black,
  pointSize: Dp = 8.dp,
  lineColor: Color = Color.Black,
  lineSize: Dp = 3.dp
) {

  val (minTime, maxTime) = remember(data) { data.first().first.toEpochMilli() to data.last().first.toEpochMilli() }
  val timeRange = remember(minTime, maxTime) {
    max((maxTime - minTime).toFloat(), 1f)
  }

  val (minValue, maxValue) = remember(data) { data.rangeOf { it.second } }
  val valueRange = remember(minValue, maxValue) { max(maxValue - minValue, 1f) }

  val points = remember(data) {
    data.map { (time, value) ->
      Offset(
        (time.toEpochMilli() - minTime) / timeRange,
        (value - minValue) / valueRange
      )
    }
  }

  val pointWeight = with(LocalDensity.current) { pointSize.toPx() }
  val lineWeight = with(LocalDensity.current) { lineSize.toPx() }

  Canvas(
    modifier
      .padding(20.dp)
      .defaultMinSize(100.dp, 100.dp)
  ) {

    drawPoints(points
      .zipWithNext()
      .flatMap { sequenceOf(it.first, it.second) }
      .map {
        Offset(
          lerp(0f, size.width, it.x),
          lerp(size.height, 0f, it.y)
        )
      }, PointMode.Lines, lineColor, lineWeight
    )

    drawPoints(points.map {
      Offset(
        lerp(0f, size.width, it.x),
        lerp(size.height, 0f, it.y)
      )
    }, PointMode.Points, pointColor, pointWeight)
  }
}

@Preview
@Composable
private fun PreviewLineChart() {
  LineChart(
    Modifier
      .size(300.dp)
      .background(Color.White),
    listOf(
      Instant.ofEpochMilli(1000) to 35.0f,
      Instant.ofEpochMilli(2000) to 36.0f,
      Instant.ofEpochMilli(4000) to 37.0f,
      Instant.ofEpochMilli(5000) to 40.0f,
      Instant.ofEpochMilli(8000) to 45.0f,
      Instant.ofEpochMilli(9000) to 65.0f,
      Instant.ofEpochMilli(10000) to 85.0f,
    ),
    pointColor = Color.Red
  )
}

@Preview
@Composable
private fun PreviewLineChartFlat() {
  LineChart(
    Modifier
      .size(300.dp)
      .background(Color.White),
    listOf(
      Instant.ofEpochMilli(1000) to 35.0f,
      Instant.ofEpochMilli(2000) to 35.0f,
    ),
    pointColor = Color.Red
  )
}

@Preview
@Composable
private fun PreviewLineChartLong() {
  LineChart(
    Modifier
      .size(300.dp)
      .background(Color.White),
    listOf(
      Instant.ofEpochMilli(1000) to 35.0f,
      Instant.ofEpochMilli(1001) to 38.0f,
      Instant.ofEpochMilli(1002) to 40.0f,
      Instant.ofEpochMilli(10000) to 40.0f,
    ),
    pointColor = Color.Red
  )
}
