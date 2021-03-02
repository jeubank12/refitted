package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class CalendarComposable {

    @Composable
    fun Calendar(days: Map<Int, Boolean>, daysPerRow: Int = 7) {
        days.keys.sorted().chunked(daysPerRow).map{ chunk ->
            Row {
                chunk.map{
                    Column {
                        CalendarDayButton(it, days.get(it))
                    }
                }
            }
        }
    }

    @Composable
    fun CalendarDayButton(day: Int, isComplete: Boolean?){
        Button(onClick = { /*TODO*/ }) {
            Text("$day")
        }
    }

    @Preview
    @Composable
    fun previewCalendar() {
        Calendar((1..28).map{it to true}.toMap())
    }
}