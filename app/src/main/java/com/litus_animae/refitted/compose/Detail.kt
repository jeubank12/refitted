package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview

@FlowPreview
object Detail {

    @Composable
    fun DetailView(day: String, workoutId: String, model: ExerciseViewModel = viewModel()) {
        LaunchedEffect(day, workoutId) {
            model.loadExercises(day, workoutId)
        }
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.weight(1f)) {
                ExerciseDetails(model)
            }
            Row(Modifier.weight(1f)) {
                ExerciseSet.ExerciseSetView(model)
            }
        }
    }

    @Composable
    private fun ExerciseDetails(model: ExerciseViewModel = viewModel()) {
        val exerciseSet by model.exerciseSet.collectAsState(initial = null)
        val exercise by model.exercise.collectAsState(initial = null)
        Column {
            Row {
                if (exerciseSet != null)
                    Text(text = exerciseSet!!.name, style = MaterialTheme.typography.h6)
            }
            Row(Modifier.padding(vertical = 5.dp)) {
                Column(Modifier.weight(1f)) {
                    val label = stringResource(id = R.string.target_reps)
                    if (exerciseSet == null)
                        Text(label)
                    else Text("$label ${exerciseSet!!.reps}")
                }
                Column(Modifier.weight(1f)) {
                    val label = stringResource(id = R.string.target_sets)
                    if (exerciseSet == null)
                        Text(label)
                    else Text("$label ${exerciseSet!!.sets}")
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
}