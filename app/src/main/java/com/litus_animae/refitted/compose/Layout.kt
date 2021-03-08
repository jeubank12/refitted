package com.litus_animae.refitted.compose

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.litus_animae.refitted.compose.Layout.Main

object Layout {

    @Composable
    fun Main() {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Athlean-X") },
                backgroundColor = MaterialTheme.colors.primary
            )
        }) {
            CalendarComposable.Calendar(
                days = 84
            )
        }
    }
}

class LayoutCompose {
    @Preview
    @Composable
    fun PreviewMain() {
        Main()
    }
}