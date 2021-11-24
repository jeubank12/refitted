package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Composable
fun ExerciseDetail(model: ExerciseViewModel = viewModel()) {
    var index by remember { mutableStateOf(0) }
    val instructions by model.exercises.collectAsState(initial = emptyList())
    val exerciseSet by instructions.getOrNull(index)?.set?.collectAsState(initial = null)
        ?: remember { mutableStateOf<ExerciseSet?>(null) }
    DetailView(index, instructions.size - 1, exerciseSet) {
        index = it
    }
}

@Preview(showBackground = true)
@FlowPreview
@Composable
fun PreviewDetailView(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
    MaterialTheme(Theme.darkColors) {
        DetailView(
            index = 0,
            maxIndex = 2,
            exerciseSet = exerciseSet
        ) {}
    }
}

@FlowPreview
@Composable
fun DetailView(
    index: Int,
    maxIndex: Int,
    exerciseSet: ExerciseSet?,
    updateIndex: (Int) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.weight(1f)) {
            ExerciseDetails(exerciseSet)
        }
        Row(Modifier.weight(1f)) {
            ExerciseSetView(index, maxIndex, updateIndex)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewExerciseDetails(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
    MaterialTheme(Theme.darkColors) {
        ExerciseDetails(
            exerciseSet = exerciseSet
        )
    }
}

@Composable
private fun ExerciseDetails(
    exerciseSet: ExerciseSet?
) {
    Column {
        Row {
            if (exerciseSet != null)
                Text(text = exerciseSet.exerciseName, style = MaterialTheme.typography.h6)
        }
        Row(Modifier.padding(vertical = 5.dp)) {
            Column(Modifier.weight(1f)) {
                val label = stringResource(id = R.string.target_reps)
                val toFailureLabel = stringResource(id = R.string.to_failure)
                when {
                    exerciseSet == null -> Text(label)
                    exerciseSet.reps < 0 -> Text("$label $toFailureLabel")
                    else -> Text("$label ${exerciseSet.reps}")
                }
            }
            Column(Modifier.weight(1f)) {
                val label = stringResource(id = R.string.target_sets)
                if (exerciseSet == null)
                    Text(label)
                else Text("$label ${exerciseSet.sets}")
            }
        }
        Row(Modifier.padding(vertical = 5.dp)) {
            if (exerciseSet != null) {
                val exercise by exerciseSet.exercise.collectAsState()
                Text(exercise?.description ?: "")
            }
        }
        Row(Modifier.padding(vertical = 5.dp)) {
            Text(exerciseSet?.note ?: "")
        }
    }
}
