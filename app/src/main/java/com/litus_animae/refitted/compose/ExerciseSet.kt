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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.state.Repetitions
import com.litus_animae.refitted.compose.state.Weight

@Composable
fun ExerciseSetView(currentIndex: Int, maxIndex: Int, updateIndex: (Int) -> Unit) {
    val weight = remember { Weight(0.0) }
    val reps = remember { Repetitions(0) }
    Column {
        Row(Modifier.weight(3f)) {
            Column(Modifier.weight(4f)) {
                WeightButtons(weight)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                RepetitionsButtons(reps)
            }
        }
        Row(Modifier.weight(1f)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val enabled = currentIndex > 0
                Button(
                    onClick = { updateIndex(currentIndex - 1) },
                    enabled = enabled
                ) {
                    val text = stringResource(id = R.string.move_left)
                    Text(text)
                }
            }
            Column(Modifier.weight(3f)) { }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val enabled = currentIndex < maxIndex
                Button(
                    onClick = { updateIndex(currentIndex + 1) },
                    enabled = enabled
                ) {
                    val text = stringResource(id = R.string.move_right)
                    Text(text)
                }
            }
        }
        Row(Modifier.weight(1f)) {
            Button(onClick = {}, Modifier.fillMaxWidth()) {
                val storeWeight by weight.value
                val storeReps by reps.value
                Text("Will store: $storeWeight lbs $storeReps reps")
            }
        }
    }
}

@Composable
@Preview(heightDp = 400)
fun PreviewExerciseSetDetails() {
    MaterialTheme(Theme.lightColors) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            ExerciseSetView(currentIndex = 5, maxIndex = 5) { }
        }
    }
}
