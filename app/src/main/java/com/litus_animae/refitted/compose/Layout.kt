package com.litus_animae.refitted.compose

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.Layout.Exercise
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.FlowPreview

@FlowPreview
object Layout {

    @Composable
    fun Main(navigateToWorkoutDay: (WorkoutDay) -> Unit, lastWorkout: WorkoutDay, model: WorkoutViewModel = viewModel()) {
        val defaultWorkout = stringResource(R.string.ax1)
        val navigateToDay: (String) -> Unit =
            { navigateToWorkoutDay(WorkoutDay(defaultWorkout, it)) }
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Athlean-X") },
                backgroundColor = MaterialTheme.colors.primary
            )
        }) {
            Calendar.Calendar(
                navigateToDay,
                defaultWorkout,
                days = 84,
                lastWorkout.day,
                model
            )
        }
    }

    @Composable
    fun Exercise(day: String, workoutId: String, model: ExerciseViewModel = viewModel()) {
        val title = stringResource(id = R.string.app_name)
        val dayWord = stringResource(id = R.string.day)
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("$title: $workoutId $dayWord $day") },
                backgroundColor = MaterialTheme.colors.primary
            )
        }) {
            Detail.DetailView(
                day = day,
                workoutId = workoutId,
                model
            )
        }
    }
}

@FlowPreview
class LayoutCompose {

    @Preview
    @Composable
    fun PreviewExercise() {
        Exercise(
            workoutId = "AX1",
            day = "2"
        )
    }
}