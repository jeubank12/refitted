package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.compose.state.Repetitions

@Composable
fun RepetitionsButtons(reps: Repetitions) {
    val currentReps by reps.value
    Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
        RepetitionButton(1, reps::plus)
    }
    Row(
        Modifier
            .size(50.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(currentReps.toString())
    }
    Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
        RepetitionButton(-1, reps::plus)
    }
}

@Composable
private fun RepetitionButton(repetitions: Int, onClick: (Int) -> Unit) {
    Button(
        onClick = { onClick(repetitions) },
        Modifier.size(50.dp),
        contentPadding = PaddingValues(5.dp)
    ) {
        Text(String.format("%+d", repetitions))
    }
}

@Composable
@Preview(heightDp = 300)
fun PreviewRepetitionButtons() {
    MaterialTheme(Theme.lightColors) {
        val reps = remember { Repetitions(8) }
        Column(Modifier.fillMaxSize()) {
            RepetitionsButtons(reps)
        }
    }
}

@Composable
@Preview(heightDp = 150, widthDp = 75)
fun PreviewSmallRepetitionButtons() {
    MaterialTheme(Theme.lightColors) {
        val reps = remember { Repetitions(8) }
        Column(Modifier.fillMaxSize()) {
            RepetitionsButtons(reps)
        }
    }
}