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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.litus_animae.refitted.util.rangeOf
import java.time.Instant
import kotlin.math.max

data class BubbleData(val time: Instant, val value: Float, val weight: Int)

@Composable
fun BubbleChart(
  modifier: Modifier = Modifier,
  data: List<BubbleData>,
  inverseRelationship: Boolean = false,
  pointColor: Color = Color.Black,
  pointSize: Dp = 8.dp,
  pointSizeMax: Dp = 30.dp,
  lineColor: Color = Color.Black,
  lineSize: Dp = 3.dp
) {

  val (minTime, maxTime) = remember(data) { data.first().time.toEpochMilli() to data.last().time.toEpochMilli() }
  val timeRange = remember(minTime, maxTime) {
    max((maxTime - minTime).toFloat(), 1f)
  }

  val (minValue, maxValue) = remember(data) { data.rangeOf { it.value } }
  val valueRange = remember(minValue, maxValue) { max(maxValue - minValue, 1f) }

  val points = remember(data) {
    data.map { (time, value, size) ->
      Offset(
        (time.toEpochMilli() - minTime) / timeRange,
        (value - minValue) / valueRange
      ) to size
    }
  }

  val (minWeight, maxWeight) = remember(data) { data.rangeOf { it.weight } }
  val weightRange = remember(minWeight, maxWeight) { max(maxWeight - minWeight, 1).toFloat() }

  val pointWeight = with(LocalDensity.current) { pointSize.toPx() }
  val pointWeightMax = with(LocalDensity.current) { pointSizeMax.toPx() }
  val lineWeight = with(LocalDensity.current) { lineSize.toPx() }

  Canvas(
    modifier
      .padding(20.dp)
      .defaultMinSize(100.dp, 100.dp)
  ) {

    drawPoints(points
      .zipWithNext()
      .flatMap { sequenceOf(it.first, it.second) }
      .map { (it) ->
        Offset(
          lerp(0f, size.width, it.x),
          lerp(size.height, 0f, it.y)
        )
      }, PointMode.Lines, lineColor, lineWeight
    )

    val pointWeightStart = if (inverseRelationship) pointWeightMax else pointWeight
    val pointWeightEnd = if (inverseRelationship) pointWeight else pointWeightMax

    points.forEach { (it, weight) ->
      drawPoints(
        listOf(
          Offset(
            lerp(0f, size.width, it.x),
            lerp(size.height, 0f, it.y)
          )
        ),
        PointMode.Points,
        pointColor,
        lerp(pointWeightStart, pointWeightEnd, (weight - minWeight) / weightRange),
        StrokeCap.Round
      )
    }
  }
}

@Preview
@Composable
private fun PreviewBubbleChart() {
  BubbleChart(
    Modifier
      .size(300.dp)
      .background(Color.White),
    listOf(
      BubbleData(Instant.ofEpochMilli(1000), 35.0f, 12),
      BubbleData(Instant.ofEpochMilli(2000), 36.0f, 10),
      BubbleData(Instant.ofEpochMilli(4000), 37.0f, 8),
      BubbleData(Instant.ofEpochMilli(5000), 40.0f, 15),
      BubbleData(Instant.ofEpochMilli(8000), 45.0f, 6),
      BubbleData(Instant.ofEpochMilli(9000), 65.0f, 12),
      BubbleData(Instant.ofEpochMilli(10000), 85.0f, 10),
    ),
    pointColor = Color.Red
  )
}

@Preview
@Composable
private fun PreviewBubbleChartFlat() {
  BubbleChart(
    Modifier
      .size(300.dp)
      .background(Color.White),
    listOf(
      BubbleData(Instant.ofEpochMilli(1000), 35.0f, 12),
      BubbleData(Instant.ofEpochMilli(2000), 35.0f, 12),
    ),
    pointColor = Color.Red
  )
}

@Preview
@Composable
private fun PreviewBubbleChartLong() {
  BubbleChart(
    Modifier
      .size(300.dp)
      .background(Color.White),
    listOf(
      BubbleData(Instant.ofEpochMilli(1000), 35.0f, 12),
      BubbleData(Instant.ofEpochMilli(1001), 38.0f, 10),
      BubbleData(Instant.ofEpochMilli(1002), 40.0f, 7),
      BubbleData(Instant.ofEpochMilli(1002), 40.0f, 8),
    ),
    pointColor = Color.Red
  )
}
