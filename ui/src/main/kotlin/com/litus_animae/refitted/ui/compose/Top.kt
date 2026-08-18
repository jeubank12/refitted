package com.litus_animae.refitted.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme as M2Theme
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.litus_animae.refitted.ui.compose.calendar.Calendar
import com.litus_animae.refitted.ui.compose.exercise.Exercise
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import com.litus_animae.refitted.ui.models.UserViewModel
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.models.WorkoutViewModel
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
      val navigateToWorkoutDay: (WorkoutPlan, Int, Boolean) -> Unit =
        { wp, day, editing -> controller.navigate("exercise/${wp.workout}/$day/$editing") }
      M3Theme(colorScheme = Theme.darkScheme) {
        Calendar(Modifier.fillMaxSize(), navigateToWorkoutDay, workoutModel, userModel)
      }
    }
    // "editing" gates the add-exercise affordance - only reachable from the edit-mode calendar
    // (Calendar.kt's DayEditDialog), so a plan can't be built up by tapping into a day normally.
    // Add-exercise itself lives entirely inside Exercise() as an overlay, not a separate
    // destination - see exercise/Main.kt.
    composable("exercise/{workout}/{day}/{editing}") {
      val exerciseModel: ExerciseViewModel = hiltViewModel(it)
      val workoutModel: WorkoutViewModel = hiltViewModel(it)
      val userModel: UserViewModel = hiltViewModel(it)
      val workoutId = it.arguments?.getString("workout")
      val day = it.arguments?.getString("day")
      val editing = it.arguments?.getString("editing")?.toBoolean() == true
      if (workoutId != null && day != null) {
        // M2 theme for now - flips to M3 once this screen's subtree is fully migrated
        // (ui/CLAUDE.md's M2->M3 migration plan).
        M2Theme(colors = Theme.darkColors) {
          Exercise(
            day = day,
            workoutId = workoutId,
            editing = editing,
            exerciseModel = exerciseModel,
            workoutModel = workoutModel,
            userModel = userModel
          )
        }
      } else {
        controller.navigate("calendar")
      }
    }
  }
}
