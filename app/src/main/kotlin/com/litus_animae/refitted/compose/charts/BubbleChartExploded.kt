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

@Composable
fun BubbleChartExploded(
  modifier: Modifier = Modifier,
  data: List<BubbleData>,
  inverseRelationship: Boolean = false,
  pointColor: Color = Color.Black,
  pointSize: Dp = 8.dp,
  pointSizeMax: Dp = 30.dp,
  lineColor: Color = Color.Black,
  lineSize: Dp = 3.dp
) {

  val indices = remember(data) {
    data.zipWithNext().scan(0) { idx, (a, b) ->
      val daysBetween = (b.time.toEpochMilli() - a.time.toEpochMilli()) / 86400000
      if (daysBetween > 0) {
        idx + daysBetween.toInt()
      } else {
        idx + 1
      }
    }
  }
  val maxIndex = (indices.lastOrNull() ?: 1).toFloat()

  val (minValue, maxValue) = remember(data) { data.rangeOf { it.value } }
  val valueRange = remember(minValue, maxValue) { max(maxValue - minValue, 1f) }

  val points = remember(data) {
    data.mapIndexed { idx, (_, value, size) ->
      Offset(
        indices.getOrElse(idx) { 0 } / maxIndex,
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
private fun PreviewBubbleChartExploded() {
  BubbleChartExploded(
    Modifier
      .size(300.dp)
      .background(Color.White),
    listOf(
      BubbleData(Instant.ofEpochMilli(86400000), 35.0f, 12),
      BubbleData(Instant.ofEpochMilli(86460000), 36.0f, 10),
      BubbleData(Instant.ofEpochMilli(86520000), 37.0f, 8),
      BubbleData(Instant.ofEpochMilli(86400000 * 2), 40.0f, 15),
      BubbleData(Instant.ofEpochMilli(86400000 * 4), 45.0f, 6),
      BubbleData(Instant.ofEpochMilli(86400000 * 4 + 60000), 65.0f, 12),
      BubbleData(Instant.ofEpochMilli(86400000 * 8), 85.0f, 10),
    ),
    pointColor = Color.Red
  )
}

@Preview
@Composable
private fun PreviewBubbleChartExplodedFlat() {
  BubbleChartExploded(
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
private fun PreviewBubbleChartExplodedLong() {
  BubbleChartExploded(
    Modifier
      .size(300.dp)
      .background(Color.White),
    listOf(
      BubbleData(Instant.ofEpochMilli(1000), 35.0f, 12),
      BubbleData(Instant.ofEpochMilli(1001), 38.0f, 10),
      BubbleData(Instant.ofEpochMilli(1002), 40.0f, 7),
      BubbleData(Instant.ofEpochMilli(86400000 * 10), 40.0f, 8),
    ),
    pointColor = Color.Red
  )
}
