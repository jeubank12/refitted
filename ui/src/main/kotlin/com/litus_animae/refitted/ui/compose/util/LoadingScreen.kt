package com.litus_animae.refitted.ui.compose.util

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview

// TODO swap for androidx.compose.material3.LoadingIndicator (per M3 guidelines:
// https://m3.material.io/components/loading-indicator/guidelines) once it's a public API in
// this project's resolved Material3 version - currently present but internal.
@Preview
@Composable
fun LoadingView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}