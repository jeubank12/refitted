package com.litus_animae.refitted.compose.calendar

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.compose.util.ConstrainedButton
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.WorkoutPlan
import java.time.Instant
import kotlin.math.ceil

@Preview(showBackground = true)
@Composable
fun PreviewCalendar() {
  MaterialTheme(colors = Theme.darkColors) {
    WorkoutCalendar(
      WorkoutPlan("test", 110, 4), mapOf(
        Pair(1, Instant.ofEpochMilli(1L)),
        Pair(2, Instant.ofEpochMilli(2L))
      ),
      contentPadding = PaddingValues(0.dp)
    ) {}
  }
}

@Composable
fun WorkoutCalendar(
  plan: WorkoutPlan,
  completedDays: Map<Int, Instant>,
  contentPadding: PaddingValues,
  navigateToDay: (Int) -> Unit,
) {
  LaunchedEffect(plan) {
    Log.d("WorkoutCalendar", "Plan is $plan")
  }
  // TODO handle different screen sizes
  // spacing between is 6dp so I am guessing that we are seeing 90x100 squares.
  // we should calculate days per row to something that makes sense for orientation
  val daysPerRow = 7
  val daysInCalendar = plan.totalDays
  val cellsInGrid = ceil(daysInCalendar.toDouble() / daysPerRow).toInt() * daysPerRow
  LazyColumn(
    Modifier
      .fillMaxWidth()
      .padding(contentPadding)
      .padding(10.dp, 10.dp)
  ) {
    val rows: List<List<Int>> = (1..cellsInGrid).chunked(daysPerRow)
    item {
      Row {
        // TODO legend
      }
    }
    items(rows) { chunk ->
      Row(Modifier.fillMaxWidth()) {
        chunk.map {
          Column(
            Modifier
              .weight(1f)
              .height(50.dp)
              .padding(horizontal = 3.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            if (it > daysInCalendar) Box {}
            else CalendarDayButton(
              it,
              DayProperties(
                isDayComplete(it, completedDays, plan.workoutStartDate),
                isLastViewedDay = it == plan.lastViewedDay,
                isRestDay = plan.restDays.contains(it)
              ),
              navigateToDay
            )
          }
        }
      }
    }
  }
}

private fun isDayComplete(
  day: Int,
  completedDays: Map<Int, Instant>,
  workoutStartDate: Instant
): Boolean {
  val currentDayCompletionDate = completedDays.getOrDefault(day, Instant.ofEpochMilli(0))
  return currentDayCompletionDate.isAfter(workoutStartDate)
}

data class DayProperties(
  val isCompletedDay: Boolean,
  val isLastViewedDay: Boolean,
  val isRestDay: Boolean
)

class DayPropertiesPreviewParameterProvider : PreviewParameterProvider<DayProperties> {
  // unnamed literals are acceptable for this preview parameter
  @Suppress("BooleanLiteralArgument")
  override val values: Sequence<DayProperties> = sequenceOf(
    DayProperties(true, true, false),
    DayProperties(true, false, false),
    DayProperties(false, true, false),
    DayProperties(false, false, false),
    DayProperties(false, true, true),
    DayProperties(false, false, true)
  )
}

@Preview(widthDp = 60, heightDp = 40)
@Composable
fun PreviewCalendarDayButton(
  @PreviewParameter(DayPropertiesPreviewParameterProvider::class) properties: DayProperties
) {
  MaterialTheme(colors = Theme.darkColors) {
    CalendarDayButton(1, properties) { }
  }
}

@Composable
private fun CalendarDayButton(
  day: Int,
  properties: DayProperties,
  navigateToDay: (Int) -> Unit
) {
  val borderColor =
    if (properties.isCompletedDay) MaterialTheme.colors.secondaryVariant
    else MaterialTheme.colors.primaryVariant
  val backgroundColor = when {
    properties.isLastViewedDay -> MaterialTheme.colors.background
    properties.isCompletedDay -> MaterialTheme.colors.secondary
    else -> MaterialTheme.colors.primary
  }
  val contentColor =
    if (properties.isRestDay) contentColorFor(backgroundColor).copy(alpha = ContentAlpha.disabled)
    else contentColorFor(backgroundColor)
  ConstrainedButton(
    if (properties.isRestDay) "Rest" else String.format("%d", day),
    if (properties.isRestDay) "Rest day $day" else "Day $day",
    onClick = { navigateToDay(day) },
    border = if (properties.isLastViewedDay) BorderStroke(6.dp, borderColor) else null,
    colors = ButtonDefaults.buttonColors(
      backgroundColor = backgroundColor,
      contentColor = contentColor
    ),
    contentPadding = PaddingValues(1.dp)
  )
}
