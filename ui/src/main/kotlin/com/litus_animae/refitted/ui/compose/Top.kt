package com.litus_animae.refitted.ui.compose

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.litus_animae.refitted.ui.compose.calendar.Calendar
import com.litus_animae.refitted.ui.compose.exercise.Exercise
import com.litus_animae.refitted.ui.compose.exercise.add.ExercisePickerList
import com.litus_animae.refitted.ui.compose.exercise.add.MuscleGroupPickerScreen
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
      Calendar(Modifier.fillMaxSize(), navigateToWorkoutDay, workoutModel, userModel)
    }
    // "editing" gates the add-exercise affordance - only reachable from the edit-mode calendar
    // (Calendar.kt's DayEditDialog), so a plan can't be built up by tapping into a day normally.
    composable("exercise/{workout}/{day}/{editing}") {
      val exerciseModel: ExerciseViewModel = hiltViewModel(it)
      val workoutModel: WorkoutViewModel = hiltViewModel(it)
      val workoutId = it.arguments?.getString("workout")
      val day = it.arguments?.getString("day")
      val editing = it.arguments?.getString("editing")?.toBoolean() == true
      // Consumed once per return to this destination - set by the add-exercise flow below
      // just before popping back here, so the pager can land on it instead of page 0.
      val scrollToExerciseName = remember(it) {
        it.savedStateHandle.remove<String>("justAddedExercise")
      }
      if (workoutId != null && day != null) {
        Exercise(
          day = day,
          workoutId = workoutId,
          editing = editing,
          exerciseModel = exerciseModel,
          workoutModel = workoutModel,
          onAddExercise = { controller.navigate("add-exercise/$workoutId/$day") },
          scrollToExerciseName = scrollToExerciseName
        )
      } else {
        controller.navigate("calendar")
      }
    }
    composable("add-exercise/{workout}/{day}") {
      val workoutId = it.arguments?.getString("workout")
      val day = it.arguments?.getString("day")
      if (workoutId != null && day != null) {
        MuscleGroupPickerScreen(
          onContinue = { muscle ->
            controller.navigate("add-exercise/$workoutId/$day/${Uri.encode(muscle)}")
          },
          onClose = { controller.popBackStack() }
        )
      } else {
        controller.navigate("calendar")
      }
    }
    composable("add-exercise/{workout}/{day}/{muscle}") {
      val exerciseModel: ExerciseViewModel = hiltViewModel(it)
      val workoutId = it.arguments?.getString("workout")
      val day = it.arguments?.getString("day")
      val muscle = it.arguments?.getString("muscle")?.let(Uri::decode)
      if (workoutId != null && day != null && muscle != null) {
        val localExercises by remember(muscle) { exerciseModel.exercisesByMuscle(muscle) }
          .collectAsStateWithLifecycle(initialValue = emptyList())
        val accessibleWorkoutNames by exerciseModel.accessibleWorkoutNames
          .collectAsStateWithLifecycle(initialValue = emptyList())
        ExercisePickerList(
          muscle = muscle,
          // Exclude the plan being built itself - a custom plan is assembled from admin
          // content, so any local rows under its own name just duplicate an admin section.
          localExercisesByWorkout = localExercises
            .filter { it.workout != workoutId }
            .groupBy { it.workout },
          accessibleWorkoutNames = accessibleWorkoutNames,
          remoteExercisesByWorkout = exerciseModel.remoteExercisesByWorkout,
          loadingWorkouts = exerciseModel.loadingWorkouts,
          onLoadWorkout = { workout -> exerciseModel.loadRemoteExercises(workout, muscle) },
          onPick = { exercise ->
            exerciseModel.addExercise(workoutId, day, exercise.id)
            controller.getBackStackEntry("exercise/{workout}/{day}/{editing}")
              .savedStateHandle["justAddedExercise"] = exercise.name
            // Pop the whole add-exercise sub-flow at once, back to the day screen.
            controller.popBackStack("exercise/{workout}/{day}/{editing}", inclusive = false)
          },
          onBack = { controller.popBackStack() }
        )
      } else {
        controller.navigate("calendar")
      }
    }
  }
}
