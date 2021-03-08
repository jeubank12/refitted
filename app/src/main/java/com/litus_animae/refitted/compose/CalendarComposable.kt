package com.litus_animae.refitted.compose

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.ExerciseDetailViewActivity
import com.litus_animae.refitted.compose.CalendarComposable.Calendar
import com.litus_animae.refitted.models.WorkoutViewModel
import java.time.Instant
import java.util.*
import kotlin.math.ceil
import kotlin.math.min

object CalendarComposable {

    @Composable
    fun Calendar(
        days: Int,
        daysPerRow: Int = 7
    ) {
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
                            else CalendarDayButton(it)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CalendarDayButton(
        day: Int,
        workoutViewModel: WorkoutViewModel = viewModel()
    ) {
        val context = LocalContext.current
        val isCompletedState = Transformations.map(workoutViewModel.completedDays) { dayRecords ->
            val maxDay = dayRecords.keys.maxOrNull() ?: day
            val maxDayCompletionDate =
                dayRecords.getOrDefault(maxDay, Date(1L))
            val currentDayCompletionDate = dayRecords.getOrDefault(day, Date(0L))
            day == maxDay || currentDayCompletionDate.after(maxDayCompletionDate)
        }.observeAsState(initial = false)
        val isCompleted: Boolean by isCompletedState
        Button(
            onClick = {
                val intent = Intent(context, ExerciseDetailViewActivity::class.java)
//            if (planSwitch != null && planSwitch.isChecked) {
//                intent.putExtra("workout", "Inferno Size")
//            } else {
                intent.putExtra("workout", "AX1")
//            }
                intent.putExtra("day", day)
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = if (isCompleted) MaterialTheme.colors.secondary else MaterialTheme.colors.primary)
        ) {
            Text(
                String.format("%d", day),
                textAlign = TextAlign.Center
            )
        }
    }
}

class CalendarCompose {
    @Preview
    @Composable
    fun PreviewCalendar() {
        MaterialTheme(colors = Theme.lightColors) {
            Calendar(84)
        }
    }
}