package com.litus_animae.refitted.ui.compose.calendar

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.data.models.WorkoutPlan
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Preview(showBackground = true)
@Composable
fun PreviewCalendar() {
  MaterialTheme(colors = Theme.darkColors) {
    WorkoutCalendar(
      WorkoutPlan("test", 110, 4, Instant.now().minus(3, ChronoUnit.DAYS)), mapOf(
        Pair(1, Instant.ofEpochMilli(1L)),
        Pair(2, Instant.ofEpochMilli(2L))
      ),
      contentPadding = PaddingValues(0.dp)
    ) {}
  }
}

@Preview(showBackground = true)
@Composable
fun PreviewCalendarUnaligned() {
  MaterialTheme(colors = Theme.darkColors) {
    WorkoutCalendar(
      WorkoutPlan("test", 110, 1),
      emptyMap(),
      contentPadding = PaddingValues(0.dp),
      onPickStartDate = {}
    ) {}
  }
}

@Composable
fun WorkoutCalendar(
  plan: WorkoutPlan,
  completedDays: Map<Int, Instant>,
  contentPadding: PaddingValues,
  onPickStartDate: (() -> Unit)? = null,
  navigateToDay: (Int) -> Unit,
) {
  LaunchedEffect(plan) {
    Log.d("WorkoutCalendar", "Plan is $plan")
  }
  val zone = remember { ZoneId.systemDefault() }
  val today = remember { LocalDate.now(zone) }
  // Epoch is the WorkoutPlan default and marks a plan as unaligned - see
  // WorkoutViewModel.alignToDayIfUnaligned/setStartDate.
  val aligned = plan.workoutStartDate.toEpochMilli() != 0L
  val anchorDate = if (aligned) plan.workoutStartDate.atZone(zone).toLocalDate() else today

  var displayedMonthKey by rememberSaveable {
    mutableIntStateOf(today.year * 12 + (today.monthValue - 1))
  }
  val displayedMonth = remember(displayedMonthKey) {
    YearMonth.of(displayedMonthKey / 12, displayedMonthKey % 12 + 1)
  }

  var hideRestDays by rememberSaveable { mutableStateOf(false) }

  val firstOfMonth = displayedMonth.atDay(1)
  // Sunday-first grid: ISO Sunday (7) should wrap to 0 leading cells, not 7.
  val leadingOffset = firstOfMonth.dayOfWeek.value % 7
  val gridStart = firstOfMonth.minusDays(leadingOffset.toLong())
  val totalCells = ceil((leadingOffset + displayedMonth.lengthOfMonth()) / 7.0).toInt() * 7
  val weeks: List<List<LocalDate>> =
    (0 until totalCells).map { gridStart.plusDays(it.toLong()) }.chunked(7)

  LazyColumn(
    Modifier
      .fillMaxWidth()
      .padding(contentPadding)
      .padding(10.dp, 10.dp)
  ) {
    if (onPickStartDate != null) {
      item { StartDatePrompt(onPickStartDate) }
    }
    item {
      MonthNavRow(
        displayedMonth,
        onPrevious = { displayedMonthKey -= 1 },
        onNext = { displayedMonthKey += 1 }
      )
    }
    item { CalendarLegend() }
    item { HideRestDaysRow(hideRestDays) { hideRestDays = it } }
    item { WeekdayHeader() }
    items(weeks) { week ->
      Row(Modifier.fillMaxWidth()) {
        week.forEach { cellDate ->
          val workoutDay = ChronoUnit.DAYS.between(anchorDate, cellDate).toInt() + 1
          val inRange = YearMonth.from(cellDate) == displayedMonth && workoutDay in 1..plan.totalDays
          Box(
            Modifier
              .weight(1f)
              .height(52.dp)
              .padding(3.dp)
          ) {
            if (!inRange) {
              OutOfRangeDayCell(cellDate.dayOfMonth)
            } else {
              val isRestDay = plan.restDays.contains(workoutDay)
              if (isRestDay && hideRestDays) {
                Box(Modifier.fillMaxSize())
              } else {
                CalendarDayCell(
                  cellDate.dayOfMonth,
                  workoutDay,
                  DayProperties(
                    isCompletedDay = aligned && isDayComplete(
                      workoutDay,
                      completedDays,
                      plan.workoutStartDate
                    ),
                    isLastViewedDay = workoutDay == plan.lastViewedDay,
                    isRestDay = isRestDay
                  ),
                  onClick = { navigateToDay(workoutDay) }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StartDatePrompt(onPickStartDate: () -> Unit) {
  Surface(
    Modifier
      .fillMaxWidth()
      .padding(bottom = 12.dp),
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colors.primary,
    contentColor = MaterialTheme.colors.onPrimary,
    elevation = 1.dp
  ) {
    Column(Modifier.padding(14.dp)) {
      // TODO localize
      Text("Pick the day you'll start this program", fontSize = 13.sp)
      Spacer(Modifier.height(8.dp))
      Button(onClick = onPickStartDate, modifier = Modifier.fillMaxWidth()) {
        // TODO localize
        Text("Set start date")
      }
    }
  }
}

@Composable
private fun MonthNavRow(
  displayedMonth: YearMonth,
  onPrevious: () -> Unit,
  onNext: () -> Unit
) {
  Row(
    Modifier
      .fillMaxWidth()
      .padding(bottom = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Surface(shape = CircleShape, modifier = Modifier.size(32.dp), elevation = 1.dp) {
      IconButton(onClick = onPrevious) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          // TODO localize
          "previous month",
          tint = MaterialTheme.colors.primary
        )
      }
    }
    Text(
      displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
      fontSize = 17.sp,
      fontWeight = FontWeight.SemiBold
    )
    Surface(shape = CircleShape, modifier = Modifier.size(32.dp), elevation = 1.dp) {
      IconButton(onClick = onNext) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowRight,
          // TODO localize
          "next month",
          tint = MaterialTheme.colors.primary
        )
      }
    }
  }
}

@Composable
private fun CalendarLegend() {
  Surface(
    Modifier
      .fillMaxWidth()
      .padding(bottom = 10.dp),
    shape = RoundedCornerShape(10.dp),
    elevation = 1.dp
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(12.dp, 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      LegendEntry("upcoming", MaterialTheme.colors.primary)
      LegendEntry("completed", MaterialTheme.colors.secondary)
      LegendEntry(
        "last viewed",
        MaterialTheme.colors.background,
        outlineColor = MaterialTheme.colors.primaryVariant
      )
      LegendEntry("rest day", MaterialTheme.colors.primary, alpha = 0.35f)
    }
  }
}

@Composable
private fun LegendEntry(
  label: String,
  color: Color,
  alpha: Float = 1f,
  outlineColor: Color? = null
) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
    Box(
      Modifier
        .size(11.dp)
        .alpha(alpha)
        .background(color, RoundedCornerShape(3.dp))
        .let { if (outlineColor != null) it.border(3.dp, outlineColor, RoundedCornerShape(3.dp)) else it }
    )
    Text(label, fontSize = 11.sp)
  }
}

@Composable
private fun HideRestDaysRow(hideRestDays: Boolean, onToggle: (Boolean) -> Unit) {
  Surface(
    Modifier
      .fillMaxWidth()
      .padding(bottom = 12.dp),
    shape = RoundedCornerShape(10.dp),
    elevation = 1.dp
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(14.dp, 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // TODO localize
      Text("Hide rest days", fontSize = 14.sp)
      Switch(checked = hideRestDays, onCheckedChange = onToggle)
    }
  }
}

@Composable
private fun WeekdayHeader() {
  Row(Modifier.fillMaxWidth()) {
    listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
      Text(
        label,
        Modifier
          .weight(1f)
          .padding(bottom = 6.dp),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
      )
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

@Preview(widthDp = 60, heightDp = 52)
@Composable
fun PreviewCalendarDayButton(
  @PreviewParameter(DayPropertiesPreviewParameterProvider::class) properties: DayProperties
) {
  MaterialTheme(colors = Theme.darkColors) {
    CalendarDayCell(1, 1, properties) { }
  }
}

@Composable
private fun OutOfRangeDayCell(dayOfMonth: Int) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      "$dayOfMonth",
      fontSize = 13.sp,
      color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
    )
  }
}

@Composable
private fun CalendarDayCell(
  dayOfMonth: Int,
  workoutDay: Int,
  properties: DayProperties,
  onClick: () -> Unit
) {
  val backgroundColor = when {
    properties.isLastViewedDay -> MaterialTheme.colors.background
    properties.isCompletedDay -> MaterialTheme.colors.secondary
    else -> MaterialTheme.colors.primary
  }
  val contentColor = contentColorFor(backgroundColor)
  val border = if (properties.isLastViewedDay)
    BorderStroke(3.dp, MaterialTheme.colors.primaryVariant) else null
  val label = if (properties.isRestDay) "Rest day $workoutDay" else "Day $workoutDay"

  Surface(
    modifier = Modifier
      .fillMaxSize()
      .alpha(if (properties.isRestDay) 0.45f else 1f)
      .clickable(onClickLabel = label, onClick = onClick),
    shape = RoundedCornerShape(8.dp),
    color = backgroundColor,
    contentColor = contentColor,
    border = border
  ) {
    Column(
      Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text("$dayOfMonth", fontSize = 14.sp)
      Text(
        if (properties.isRestDay) "rest" else "day $workoutDay",
        fontSize = 9.sp,
        modifier = Modifier.alpha(0.8f)
      )
    }
  }
}
