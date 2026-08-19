package com.litus_animae.refitted.ui.compose.calendar

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.litus_animae.refitted.ui.compose.exercise.AdaptiveExercisePanes
import com.litus_animae.refitted.ui.compose.util.ExtendedTheme
import com.litus_animae.refitted.ui.compose.util.RefittedTheme
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
  RefittedTheme(darkTheme = true) {
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
  RefittedTheme(darkTheme = true) {
    WorkoutCalendar(
      WorkoutPlan("test", 110, 1),
      emptyMap(),
      contentPadding = PaddingValues(0.dp),
      onSaveStartDate = {}
    ) {}
  }
}

@Composable
fun WorkoutCalendar(
  plan: WorkoutPlan,
  completedDays: Map<Int, Instant>,
  contentPadding: PaddingValues,
  // True when shown alongside WorkoutPlanListPane AND height is constrained (landscape) - moves
  // the legend and "hide rest days" toggle into a vertical sidebar alongside the grid instead of
  // stacking them above it, since landscape has width to spare but not much height. A Medium+
  // width window with plenty of height (e.g. an unfolded phone in portrait) stays stacked, same
  // as compact width.
  wideLayout: Boolean = false,
  editMode: Boolean = false,
  onExitEdit: () -> Unit = {},
  onSaveStartDate: (LocalDate) -> Unit = {},
  onClearDay: (day: Int) -> Unit = {},
  onSetDayRest: (day: Int, isRest: Boolean) -> Unit = { _, _ -> },
  onEditDay: (day: Int) -> Unit = {},
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

  // Tentative start day while unaligned - the calendar itself is the picker here; only
  // Save (via onSaveStartDate) persists it as plan.workoutStartDate.
  var pickedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
  val pickedDate = LocalDate.ofEpochDay(pickedEpochDay)
  val anchorDate = if (aligned) plan.workoutStartDate.atZone(zone).toLocalDate() else pickedDate

  var displayedMonthKey by rememberSaveable {
    mutableIntStateOf(today.year * 12 + (today.monthValue - 1))
  }
  val displayedMonth = remember(displayedMonthKey) {
    YearMonth.of(displayedMonthKey / 12, displayedMonthKey % 12 + 1)
  }

  var hideRestDays by rememberSaveable { mutableStateOf(false) }
  // Tapped day awaiting the edit-actions dialog - edit mode only (see onClick below).
  var editingDay by rememberSaveable { mutableStateOf<Int?>(null) }

  val firstOfMonth = displayedMonth.atDay(1)
  // Sunday-first grid: ISO Sunday (7) should wrap to 0 leading cells, not 7.
  val leadingOffset = firstOfMonth.dayOfWeek.value % 7
  val gridStart = firstOfMonth.minusDays(leadingOffset.toLong())
  val totalCells = ceil((leadingOffset + displayedMonth.lengthOfMonth()) / 7.0).toInt() * 7
  val weeks: List<List<LocalDate>> =
    (0 until totalCells).map { gridStart.plusDays(it.toLong()) }.chunked(7)

  // A local lambda (not a top-level composable) since it closes over all of the state above -
  // shared between the plain stacked layout and the wideLayout AdaptiveExercisePanes split
  // below, where it becomes the grid pane and the legend/toggle move into a separate sidebar
  // pane instead of being items in this same list.
  // Month/year nav, the legend, and the "hide rest days" toggle all move into the sidebar in
  // wideLayout instead of appearing as items here - see CalendarSidebar below.
  val gridContent: @Composable (Modifier, includeMonthLegendToggle: Boolean) -> Unit =
    { gridModifier, includeMonthLegendToggle ->
      LazyColumn(gridModifier) {
        item {
          AnimatedVisibility(visible = !aligned, exit = shrinkVertically() + fadeOut()) {
            StartDatePickerBanner(
              pickedDate,
              onSave = { onSaveStartDate(pickedDate) },
              modifier = Modifier.padding(bottom = 12.dp)
            )
          }
        }
        item {
          AnimatedVisibility(visible = editMode, exit = shrinkVertically() + fadeOut()) {
            EditModeBanner(
              isEmpty = plan.totalDays == 0,
              onDone = onExitEdit,
              modifier = Modifier.padding(bottom = 12.dp)
            )
          }
        }
        if (includeMonthLegendToggle) {
          item {
            MonthNavRow(
              displayedMonth,
              onPrevious = { displayedMonthKey -= 1 },
              onNext = { displayedMonthKey += 1 }
            )
          }
          item { CalendarLegend() }
          item { HideRestDaysRow(hideRestDays) { hideRestDays = it } }
        }
        item { WeekdayHeader() }
        items(weeks) { week ->
          Row(Modifier.fillMaxWidth()) {
            week.forEach { cellDate ->
              val workoutDay = ChronoUnit.DAYS.between(anchorDate, cellDate).toInt() + 1
              val inDisplayedMonth = YearMonth.from(cellDate) == displayedMonth
              // A plan day can fall in a leading/trailing cell that belongs to an adjacent month
              // (e.g. June 29 shown at the top of July's grid) - it's still a real day of the
              // plan and should be reachable without a month-nav round trip, just visually
              // distinguished (see CalendarDayCell's dimmed param) from the displayed month.
              val inPlanRange = workoutDay in 1..plan.totalDays
              val isRestDay = inPlanRange && plan.restDays.contains(workoutDay)
              // Edit mode needs rest days visible to manage them, even with "hide rest days" on.
              val hidden = isRestDay && hideRestDays && !editMode
              val onClick: (() -> Unit)? = when {
                !aligned && inDisplayedMonth -> ({ pickedEpochDay = cellDate.toEpochDay() })
                editMode && inPlanRange && !hidden -> ({ editingDay = workoutDay })
                aligned && inPlanRange && !hidden -> ({ navigateToDay(workoutDay) })
                else -> null
              }
              val label = when {
                !aligned && inDisplayedMonth ->
                  "Choose ${cellDate.format(DateTimeFormatter.ofPattern("MMM d"))} as start"
                editMode && inPlanRange -> "Edit day $workoutDay"
                isRestDay -> "Rest day $workoutDay"
                inPlanRange -> "Day $workoutDay"
                else -> null
              }
              Box(
                Modifier
                  .weight(1f)
                  .height(52.dp)
                  .padding(3.dp)
                  .let { base ->
                    if (onClick != null) base.clickable(onClickLabel = label, onClick = onClick)
                    else base
                  }
              ) {
                val isToday = cellDate == today
                if (!inPlanRange) {
                  OutOfRangeDayCell(cellDate.dayOfMonth, isToday = isToday)
                } else if (hidden) {
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
                      isLastViewedDay = aligned && workoutDay == plan.lastViewedDay,
                      isRestDay = isRestDay
                    ),
                    selected = !aligned && cellDate == pickedDate,
                    isToday = isToday,
                    dimmed = !inDisplayedMonth
                  )
                }
              }
            }
          }
        }
      }
    }
  val bodyModifier = Modifier
    .fillMaxWidth()
    .padding(contentPadding)
    .padding(10.dp, 10.dp)
  if (wideLayout) {
    // The grid gets the lion's share of the width; the sidebar just needs enough for the
    // legend labels and the toggle - reuses the same reflow AdaptiveExercisePanes gives the
    // exercise screen's instructions/detail and chart/list splits.
    AdaptiveExercisePanes(
      bodyModifier,
      splitRatio = 0.78f,
      gap = 12.dp,
      first = { gridContent(Modifier.fillMaxSize(), false) },
      second = {
        CalendarSidebar(
          displayedMonth,
          onPrevious = { displayedMonthKey -= 1 },
          onNext = { displayedMonthKey += 1 },
          hideRestDays = hideRestDays,
          onToggleHideRestDays = { hideRestDays = it }
        )
      }
    )
  } else {
    gridContent(bodyModifier, true)
  }

  editingDay?.let { day ->
    DayEditDialog(
      day = day,
      isRestDay = plan.restDays.contains(day),
      onDismissRequest = { editingDay = null },
      onEditDay = { onEditDay(day) },
      onClear = { onClearDay(day) },
      onSetRest = { isRest -> onSetDayRest(day, isRest) }
    )
  }
}

@Composable
private fun EditModeBanner(isEmpty: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    shadowElevation = 1.dp
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // TODO localize
      Text(
        if (isEmpty) "This is your new plan — tap + below to add your first day"
        else "Editing plan — tap a day to change it",
        Modifier
          .weight(1f)
          .padding(end = 8.dp),
        fontSize = 13.sp
      )
      Button(onClick = onDone) {
        // TODO localize
        Text("Done")
      }
    }
  }
}

@Composable
private fun StartDatePickerBanner(
  pickedDate: LocalDate,
  onSave: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    shadowElevation = 1.dp
  ) {
    Column(Modifier.padding(14.dp)) {
      // TODO localize
      Text("Tap a day to choose your start", fontSize = 13.sp)
      Spacer(Modifier.height(4.dp))
      Text(
        // TODO localize
        "Start: ${pickedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(Modifier.height(8.dp))
      Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
        // TODO localize
        Text("Save")
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
    Surface(shape = CircleShape, modifier = Modifier.size(32.dp), shadowElevation = 1.dp) {
      IconButton(onClick = onPrevious) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          // TODO localize
          "previous month",
          tint = MaterialTheme.colorScheme.primary
        )
      }
    }
    Text(
      displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
      fontSize = 17.sp,
      fontWeight = FontWeight.SemiBold
    )
    Surface(shape = CircleShape, modifier = Modifier.size(32.dp), shadowElevation = 1.dp) {
      IconButton(onClick = onNext) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowRight,
          // TODO localize
          "next month",
          tint = MaterialTheme.colorScheme.primary
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
    shadowElevation = 1.dp
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(12.dp, 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      CalendarLegendEntries()
    }
  }
}

@Composable
private fun CalendarLegendEntries() {
  val isDark = isSystemInDarkTheme()
  LegendEntry("upcoming", MaterialTheme.colorScheme.primary)
  LegendEntry(
    "completed",
    // Solid dark chip in light mode; matches the app's card surfaces in dark mode instead of
    // standing out as a bright inverse chip (see CalendarDayCell's same choice) - outlined
    // here so the legend swatch itself stays visible against its own matching background.
    if (isDark) CardDefaults.cardColors().containerColor else MaterialTheme.colorScheme.inverseSurface,
    outlineColor = if (isDark) MaterialTheme.colorScheme.outline else null
  )
  LegendEntry(
    "last viewed",
    MaterialTheme.colorScheme.background,
    outlineColor = MaterialTheme.colorScheme.primaryContainer
  )
  LegendEntry("rest day", MaterialTheme.colorScheme.primary, alpha = 0.35f)
}

/**
 * Month/year nav, [CalendarLegend], and [HideRestDaysRow] combined into a vertical bar alongside
 * the grid instead of stacked above it - used when there's width to spare but not much height
 * (see [WorkoutCalendar]'s wideLayout), via [AdaptiveExercisePanes].
 */
@Composable
private fun CalendarSidebar(
  displayedMonth: YearMonth,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  hideRestDays: Boolean,
  onToggleHideRestDays: (Boolean) -> Unit
) {
  Surface(
    Modifier.fillMaxSize(),
    shape = RoundedCornerShape(10.dp),
    shadowElevation = 1.dp
  ) {
    Column(
      Modifier
        .fillMaxSize()
        .padding(12.dp, 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(
        // Abbreviated - this bar is narrow, unlike MonthNavRow's full-width portrait home.
        displayedMonth.format(DateTimeFormatter.ofPattern("MMM yy")),
        Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
      )
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
      ) {
        Surface(shape = CircleShape, modifier = Modifier.size(32.dp), shadowElevation = 1.dp) {
          IconButton(onClick = onPrevious) {
            Icon(
              Icons.AutoMirrored.Filled.KeyboardArrowLeft,
              // TODO localize
              "previous month",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }
        Surface(shape = CircleShape, modifier = Modifier.size(32.dp), shadowElevation = 1.dp) {
          IconButton(onClick = onNext) {
            Icon(
              Icons.AutoMirrored.Filled.KeyboardArrowRight,
              // TODO localize
              "next month",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }
      }
      HorizontalDivider()
      CalendarLegendEntries()
      HorizontalDivider()
      // TODO localize
      Text("Hide rest days", fontSize = 14.sp)
      Switch(checked = hideRestDays, onCheckedChange = onToggleHideRestDays)
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
    shadowElevation = 1.dp
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
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
  RefittedTheme(darkTheme = true) {
    CalendarDayCell(1, 1, properties)
  }
}

@Composable
private fun OutOfRangeDayCell(dayOfMonth: Int, isToday: Boolean = false) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      "$dayOfMonth",
      fontSize = 13.sp,
      color = if (isToday) ExtendedTheme.colors.goodAttention.color
      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
      fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
    )
  }
}

@Composable
private fun CalendarDayCell(
  dayOfMonth: Int,
  workoutDay: Int,
  properties: DayProperties,
  selected: Boolean = false,
  isToday: Boolean = false,
  dimmed: Boolean = false
) {
  // Last-viewed (aligned) and selected-as-start (unaligned) are mutually exclusive - one
  // outline style covers "this is the reference day" in either mode.
  val highlighted = properties.isLastViewedDay || selected
  val isDark = isSystemInDarkTheme()
  val backgroundColor = when {
    highlighted -> MaterialTheme.colorScheme.background
    // Old M2 secondary (#212121) doubled as both the "secondary" role and the literal
    // completed-day color; the generated M3 secondary doesn't preserve that. In light mode
    // inverseSurface gives the same solid, high-emphasis dark chip (#2E3036, close to the
    // original black). In dark mode that same chip would flip to a bright light color and read
    // as shouting rather than "done" - use the same tonal container Cards render with instead,
    // so completed cells blend with the rest of the app's card surfaces.
    properties.isCompletedDay -> if (isDark) CardDefaults.cardColors().containerColor else MaterialTheme.colorScheme.inverseSurface
    else -> MaterialTheme.colorScheme.primary
  }
  // contentColorFor(backgroundColor) doesn't resolve inverseSurface/inverseOnSurface (or the
  // Card container tone) as a pair, so each branch picks its own "on" color explicitly.
  val contentColor = when {
    highlighted -> MaterialTheme.colorScheme.onBackground
    properties.isCompletedDay -> if (isDark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.inverseOnSurface
    else -> MaterialTheme.colorScheme.onPrimary
  }
  val border = if (highlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer) else null
  // Rest-day and adjacent-month dimming both fade the same surface - multiply rather than
  // pick one, so a rest day that also falls outside the displayed month reads as both.
  val restDayAlpha = if (properties.isRestDay) 0.45f else 1f
  val monthAlpha = if (dimmed) 0.6f else 1f

  Surface(
    modifier = Modifier
      .fillMaxSize()
      .alpha(restDayAlpha * monthAlpha),
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
      Text(
        "$dayOfMonth",
        fontSize = 14.sp,
        color = if (isToday) ExtendedTheme.colors.goodAttention.color else contentColor,
        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
      )
      Text(
        if (properties.isRestDay) "rest" else "day $workoutDay",
        fontSize = 9.sp,
        modifier = Modifier.alpha(0.8f)
      )
    }
  }
}
