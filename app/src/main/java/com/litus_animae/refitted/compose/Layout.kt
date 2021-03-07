package com.litus_animae.refitted.compose

import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable

object Layout {
    
    @Composable
    fun Main() {
        Scaffold(topBar = {
            TopAppBar {
                Text("Athlean-X")
            }
        }) {
            CalendarComposable.Calendar(
                days = 84,
                dayStatus = emptyMap()
            )
        }
    }
}