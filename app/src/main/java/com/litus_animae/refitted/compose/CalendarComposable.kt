package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class CalendarComposable {

    @Composable
    fun Calendar(days: Map<Int, Boolean>, daysPerRow: Int = 7) {
        Column(Modifier.fillMaxWidth().padding(10.dp, 10.dp)) {
            days.keys.sorted().chunked(daysPerRow).map { chunk ->
                Row(Modifier.fillMaxWidth()) {
                    chunk.map {
                        Column(Modifier.weight(1f).height(75.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                            CalendarDayButton(it, days.get(it))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CalendarDayButton(day: Int, isComplete: Boolean?){
        Button(onClick = { /*TODO*/ },
        Modifier.padding(1.dp, 8.dp)) {
            Text(String.format("%d", day))
        }
    }

    @Preview(showSystemUi = true)
    @Composable
    fun PreviewCalendar() {
        Calendar((1..25).map{it to true}.toMap())
    }
}