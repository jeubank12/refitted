package com.litus_animae.refitted.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltNavGraphViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.FlowPreview

@FlowPreview
object Main {

    @Composable
    fun Top() {
        val controller = rememberNavController()
        NavHost(controller, startDestination = "calendar") {
            composable("calendar") {
                val model: WorkoutViewModel = hiltViewModel(it)
                Layout.Main(controller, model)
            }
            composable("exercise/{workout}/{day}") {
                val model: ExerciseViewModel = hiltViewModel(it)
                val workoutId = it.arguments?.getString("workout")
                val day = it.arguments?.getString("day")
                if (workoutId != null && day != null)
                    Layout.Exercise(
                        day = day,
                        workoutId = workoutId,
                        model = model
                    )
            }
        }

    }
}