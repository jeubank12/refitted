package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.state.Record
import com.litus_animae.refitted.compose.state.Repetitions
import com.litus_animae.refitted.compose.state.Weight
import com.litus_animae.refitted.models.ExerciseSet

@Composable
fun ExerciseSetView(
    exerciseSet: ExerciseSet,
    record: Record,
    currentIndex: Int,
    maxIndex: Int,
    updateIndex: (Int, Record) -> Unit,
    onSave: (Record) -> Unit
) {
    val weight = remember(exerciseSet, record) { Weight(record.weight) }
    val reps = remember(exerciseSet, record) { Repetitions(record.reps) }
    val saveWeight by weight.value
    val saveReps by reps.value
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
                    onClick = {
                        updateIndex(
                            currentIndex - 1,
                            record.copy(weight = saveWeight, reps = saveReps)
                        )
                    },
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
                    onClick = {
                        updateIndex(
                            currentIndex + 1,
                            record.copy(weight = saveWeight, reps = saveReps)
                        )
                    },
                    enabled = enabled
                ) {
                    val text = stringResource(id = R.string.move_right)
                    Text(text)
                }
            }
        }
        Row(Modifier.weight(1f)) {
            Column {
                var timerRunning by remember { mutableStateOf(false) }
                Timer(timerRunning, exerciseSet.rest * 1000L) { timerRunning = false }
                Button(
                    onClick = {
                        onSave(record.copy(weight = saveWeight, reps = saveReps))
                        timerRunning = true
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !timerRunning
                ) {
                    Text("Will store: $saveWeight lbs $saveReps reps")
                }
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
            ExerciseSetView(
                exampleExerciseSet,
                Record(25.0, exampleExerciseSet.reps, exampleExerciseSet),
                currentIndex = 5,
                maxIndex = 5,
                updateIndex = { _, _ -> },
                onSave = {}
            )
        }
    }
}
