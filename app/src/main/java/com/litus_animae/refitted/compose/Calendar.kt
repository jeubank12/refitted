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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import java.util.*
import kotlin.math.ceil
import kotlin.math.min

@Preview(showBackground = true)
@Composable
fun PreviewCalendar() {
    MaterialTheme(colors = Theme.darkColors) {
        Calendar(
            10, "4", mapOf(
                Pair(1, Date(1L)),
                Pair(2, Date(2L))
            )
        ) {}
    }
}

@Composable
fun Calendar(
    days: Int,
    lastViewedDay: String,
    completedDays: Map<Int, Date>,
    navigateToDay: (String) -> Unit,
) {
    // TODO handle different screen sizes
    val daysPerRow = 7
    // TODO adjust TextSize to handle 3 digit numbers
    val daysInCalendar = min(days, 99)
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
                            .padding(horizontal = 3.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (it > daysInCalendar) Box {}
                        else CalendarDayButton(
                            it,
                            DayProperties(
                                isDayComplete(it, completedDays),
                                isLastViewedDay = it.toString() == lastViewedDay
                            ),
                            navigateToDay
                        )
                    }
                }
            }
        }
    }
}

private fun isDayComplete(day: Int, completedDays: Map<Int, Date>): Boolean {
    val maxDay = completedDays.keys.maxOrNull() ?: return false
    val maxDayCompletionDate =
        completedDays.getOrDefault(maxDay, Date(1L))
    val currentDayCompletionDate = completedDays.getOrDefault(day, Date(0L))
    return day == maxDay || currentDayCompletionDate.after(maxDayCompletionDate)
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

@Preview
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
    navigateToDay: (String) -> Unit
) {
    val borderColor =
        if (properties.isCompletedDay) MaterialTheme.colors.secondaryVariant else MaterialTheme.colors.primaryVariant
    val backgroundColor = when {
        properties.isLastViewedDay -> MaterialTheme.colors.background
        properties.isCompletedDay -> MaterialTheme.colors.secondary
        else -> MaterialTheme.colors.primary
    }
    Button(
        onClick = { navigateToDay(day.toString()) },
        border = if (properties.isLastViewedDay) BorderStroke(2.dp, borderColor) else null,
        colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor)
    ) {
        Text(
            String.format("%d", day),
            textAlign = TextAlign.Center
        )
    }
}
