package com.litus_animae.refitted.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.litus_animae.refitted.compose.calendar.Calendar
import com.litus_animae.refitted.compose.exercise.Exercise
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
  val controller = rememberNavController()
  NavHost(controller, startDestination = "calendar", Modifier.fillMaxSize()) {
    composable("calendar") {
      val workoutModel: WorkoutViewModel = hiltViewModel(it)
      val userModel: UserViewModel = hiltViewModel(it)
      val navigateToWorkoutDay: (WorkoutPlan, Int) -> Unit =
        { wp, day -> controller.navigate("exercise/${wp.workout}/$day") }
      Calendar(Modifier.fillMaxSize(), navigateToWorkoutDay, workoutModel, userModel)
    }
    composable("exercise/{workout}/{day}") {
      val exerciseModel: ExerciseViewModel = hiltViewModel(it)
      val workoutModel: WorkoutViewModel = hiltViewModel(it)
      val workoutId = it.arguments?.getString("workout")
      val day = it.arguments?.getString("day")
      if (workoutId != null && day != null) {
        Exercise(
          day = day,
          workoutId = workoutId,
          exerciseModel = exerciseModel,
          workoutModel = workoutModel
        )
      } else {
        controller.navigate("calendar")
      }
    }
  }
}
