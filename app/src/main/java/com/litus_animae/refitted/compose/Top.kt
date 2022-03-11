package com.litus_animae.refitted.compose

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.litus_animae.refitted.compose.calendar.Calendar
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.UserViewModel
import com.litus_animae.refitted.models.WorkoutPlan
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalCoroutinesApi::class)
@FlowPreview
@Composable
fun Top() {
    // TODO doesn't survive warm restarts....
    val controller = rememberNavController()
    NavHost(controller, startDestination = "calendar") {
        composable("calendar") {
            val model: WorkoutViewModel = hiltViewModel(it)
            val userModel: UserViewModel = hiltViewModel(it)
            val navigateToWorkoutDay: (WorkoutPlan, Int) -> Unit =
                { wp, day -> controller.navigate("exercise/${wp.workout}/$day") }
            SignInUser(userModel) { Calendar(navigateToWorkoutDay, model) }
        }
        composable("exercise/{workout}/{day}") {
            val model: ExerciseViewModel = hiltViewModel(it)
            val userModel: UserViewModel = hiltViewModel(it)
            val workoutId = it.arguments?.getString("workout")
            val day = it.arguments?.getString("day")
            if (workoutId != null && day != null) {
                SignInUser(userModel) {
                    Exercise(
                        day = day,
                        workoutId = workoutId,
                        model = model
                    )
                }
            } else {
                controller.navigate("calendar")
            }
        }
    }
}
