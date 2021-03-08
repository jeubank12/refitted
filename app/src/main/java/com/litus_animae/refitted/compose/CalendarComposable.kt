package com.litus_animae.refitted.compose

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.ExerciseDetailViewActivity
import com.litus_animae.refitted.compose.CalendarComposable.Calendar
import kotlin.math.ceil

object CalendarComposable {

    @Composable
    fun Calendar(
        days: Int,
        dayStatus: Map<Int, Boolean>,
        daysPerRow: Int = 7
    ) {
        val cellsInGrid = ceil(days.toDouble() / daysPerRow).toInt() * daysPerRow
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp, 10.dp)
        ) {
            (1..cellsInGrid).chunked(daysPerRow).map { chunk ->
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
                            if (it > days) Box {}
                            else CalendarDayButton(it, dayStatus[it])
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CalendarDayButton(
        day: Int,
        isComplete: Boolean?
    ) {
        val context = LocalContext.current
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
                      }
        ) {
            Text(String.format("%d", day),
            textAlign = TextAlign.Center)
        }
    }
}

class CalendarCompose {
    @Preview
    @Composable
    fun PreviewCalendar() {
        MaterialTheme(colors = Theme.lightColors) {
            Calendar(84, emptyMap())
        }
    }
}