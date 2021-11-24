package com.litus_animae.refitted.compose

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.FlowPreview

@Composable
fun Main(
    navigateToWorkoutDay: (WorkoutDay) -> Unit,
    lastWorkout: WorkoutDay,
    model: WorkoutViewModel = viewModel()
) {
    val defaultWorkout = stringResource(R.string.ax1)
    val completedDays by model.completedDays.collectAsState(initial = emptyMap())

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Athlean-X") },
            backgroundColor = MaterialTheme.colors.primary
        )
    }) {
        Calendar(
            days = 84,
            lastWorkout.day,
            completedDays
        ) { navigateToWorkoutDay(WorkoutDay(defaultWorkout, it)) }
    }
}

@FlowPreview
@Composable
fun Exercise(day: String, workoutId: String, model: ExerciseViewModel = viewModel()) {
    val title = stringResource(id = R.string.app_name)
    val dayWord = stringResource(id = R.string.day)
    val isLoading by model.isLoading.collectAsState()
    LaunchedEffect(day, workoutId) {
        model.loadExercises(day, workoutId)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("$title: $workoutId $dayWord $day") },
            backgroundColor = MaterialTheme.colors.primary
        )
    }) {
        if (isLoading) {
            LoadingView()
        } else {
            ExerciseDetail(
                day = day,
                workoutId = workoutId,
                model
            )
        }
    }
}
