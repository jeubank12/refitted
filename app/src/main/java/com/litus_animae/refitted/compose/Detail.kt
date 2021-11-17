package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview

@FlowPreview
@Composable
fun DetailView(day: String, workoutId: String, model: ExerciseViewModel = viewModel()) {
    LaunchedEffect(day, workoutId) {
        model.loadExercises(day, workoutId)
    }
    var index by remember { mutableStateOf(0) }
    val instructions by model.exercises.collectAsState(initial = emptyList())
    val exerciseSet by instructions.getOrNull(index)?.set?.collectAsState(initial = null)
        ?: remember { mutableStateOf<com.litus_animae.refitted.models.ExerciseSet?>(null) }
    val exercise by exerciseSet?.exercise?.collectAsState(initial = null)
        ?: remember { mutableStateOf<Exercise?>(null) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.weight(1f)) {
            ExerciseDetails(exercise, exerciseSet)
        }
        Row(Modifier.weight(1f)) {
            ExerciseSetView(index, instructions.size - 1) { updatedIndex ->
                index = updatedIndex
            }
        }
    }
}

@Composable
private fun ExerciseDetails(
    exercise: Exercise?,
    exerciseSet: com.litus_animae.refitted.models.ExerciseSet?
) {
    Column {
        Row {
            if (exerciseSet != null)
                Text(text = exerciseSet.name, style = MaterialTheme.typography.h6)
        }
        Row(Modifier.padding(vertical = 5.dp)) {
            Column(Modifier.weight(1f)) {
                val label = stringResource(id = R.string.target_reps)
                if (exerciseSet == null)
                    Text(label)
                else Text("$label ${exerciseSet.reps}")
            }
            Column(Modifier.weight(1f)) {
                val label = stringResource(id = R.string.target_sets)
                if (exerciseSet == null)
                    Text(label)
                else Text("$label ${exerciseSet.sets}")
            }
        }
        Row(Modifier.padding(vertical = 5.dp)) {
            Text(exercise?.description ?: "")
        }
        Row(Modifier.padding(vertical = 5.dp)) {
            Text(exerciseSet?.note ?: "")
        }
    }
}
