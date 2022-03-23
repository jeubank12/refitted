package com.litus_animae.refitted.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.WorkoutPlan
import java.util.*
import kotlin.math.ceil

@Preview(showBackground = true)
@Composable
fun PreviewCalendar() {
    MaterialTheme(colors = Theme.darkColors) {
        WorkoutCalendar(
            WorkoutPlan("test", 110, 4), mapOf(
                Pair(1, Date(1L)),
                Pair(2, Date(2L))
            )
        ) {}
    }
}

@Composable
fun WorkoutCalendar(
    plan: WorkoutPlan,
    completedDays: Map<Int, Date>,
    navigateToDay: (Int) -> Unit,
) {
    // TODO handle different screen sizes
    // spacing between is 6dp so I am guessing that we are seeing 90x100 squares.
    // we should calculate days per row to something that makes sense for orientation
    val daysPerRow = 7
    val daysInCalendar = plan.totalDays
    val cellsInGrid = ceil(daysInCalendar.toDouble() / daysPerRow).toInt() * daysPerRow
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(10.dp, 10.dp)
    ) {
        val rows: List<List<Int>> = (1..cellsInGrid).chunked(daysPerRow)
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
                                isLastViewedDay = it == plan.lastViewedDay
                            ),
                            navigateToDay
                        )
                    }
                }
            }
        }
    }
}

private fun isDayComplete(day: Int, completedDays: Map<Int, Date>, workoutStartDate: Date): Boolean {
    val currentDayCompletionDate = completedDays.getOrDefault(day, Date(0L))
    return currentDayCompletionDate.after(workoutStartDate)
}

data class DayProperties(val isCompletedDay: Boolean, val isLastViewedDay: Boolean)
class DayPropertiesPreviewParameterProvider : PreviewParameterProvider<DayProperties> {
    override val values: Sequence<DayProperties> = sequenceOf(
        DayProperties(true, true),
        DayProperties(true, false),
        DayProperties(false, true),
        DayProperties(false, false)
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
        if (properties.isCompletedDay) MaterialTheme.colors.secondaryVariant else MaterialTheme.colors.primaryVariant
    val backgroundColor = when {
        properties.isLastViewedDay -> MaterialTheme.colors.background
        properties.isCompletedDay -> MaterialTheme.colors.secondary
        else -> MaterialTheme.colors.primary
    }
    Button(
        onClick = { navigateToDay(day) },
        border = if (properties.isLastViewedDay) BorderStroke(2.dp, borderColor) else null,
        colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor),
        contentPadding = PaddingValues(1.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val availableWidth = maxWidth
            with(LocalDensity.current) {
                val maxSize = MaterialTheme.typography.button.fontSize
                val desiredSize = if (day >= 100) availableWidth.toSp() * 0.6f
                else if (day >= 10) availableWidth.toSp() * 0.7f
                else availableWidth.toSp()
                val size = if (desiredSize > maxSize) maxSize else desiredSize
                Text(
                    String.format("%d", day),
                    fontSize = size
                )
            }
        }
    }
}
