package com.litus_animae.refitted.compose

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltNavGraphViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.WorkoutViewModel

object Main {

    @Composable
    fun Top() {
        val controller = rememberNavController()
        NavHost(controller, startDestination = "calendar") {
            composable("calendar") {
                val model: WorkoutViewModel = hiltNavGraphViewModel(it)
                Layout.Main(controller, model)
            }
            composable("exercise/{workout}/{day}") {
                val model: ExerciseViewModel = hiltNavGraphViewModel(it)
                val workoutId = it.arguments?.getString("workout")
                val day = it.arguments?.getString("day")
                if (workoutId != null && day != null)
                    Layout.Exercise(
                        workoutId,
                        day,
                        model
                    )
            }
        }

    }
}