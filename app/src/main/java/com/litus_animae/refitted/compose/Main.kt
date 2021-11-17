package com.litus_animae.refitted.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.FlowPreview

@FlowPreview
object Main {

    @Composable
    fun Top() {
        val controller = rememberNavController()
        val defaultWorkout = stringResource(R.string.ax1)
        var lastWorkout by rememberSaveable {
            mutableStateOf(
                WorkoutDay(defaultWorkout, "1")
            )
        }
        NavHost(controller, startDestination = "calendar") {
            composable("calendar") {
                val model: WorkoutViewModel = hiltViewModel(it)
                val navigateToWorkoutDay: (WorkoutDay) -> Unit =
                    { wd -> controller.navigate("exercise/${wd.workoutId}/${wd.day}") }
                Layout.Main(navigateToWorkoutDay, lastWorkout, model)
            }
            composable("exercise/{workout}/{day}") {
                val model: ExerciseViewModel = hiltViewModel(it)
                val workoutId = it.arguments?.getString("workout")
                val day = it.arguments?.getString("day")
                if (workoutId != null && day != null) {
                    lastWorkout = WorkoutDay(workoutId, day)
                    Layout.Exercise(
                        day = day,
                        workoutId = workoutId,
                        model = model
                    )
                }
            }
        }
    }
}