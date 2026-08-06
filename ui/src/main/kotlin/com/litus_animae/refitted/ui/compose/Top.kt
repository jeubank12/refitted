package com.litus_animae.refitted.ui.compose

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.credentials.CustomCredential
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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

private val alternateToArg = listOf(
  navArgument("alternateTo") {
    type = NavType.StringType
    nullable = true
    defaultValue = null
  }
)

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
          onAddAlternate = { set ->
            controller.navigate(
              "add-exercise/$workoutId/$day?alternateTo=${Uri.encode(set.primaryStep)}"
            )
          },
          scrollToExerciseName = scrollToExerciseName
        )
      } else {
        controller.navigate("calendar")
      }
    }
    // A non-null "alternateTo" carries the base step the picked exercise should become an
    // alternate of - the picker itself is identical either way, only the commit differs.
    composable("add-exercise/{workout}/{day}?alternateTo={alternateTo}", arguments = alternateToArg) {
      val workoutId = it.arguments?.getString("workout")
      val day = it.arguments?.getString("day")
      val alternateTo = it.arguments?.getString("alternateTo")?.let(Uri::decode)
      if (workoutId != null && day != null) {
        MuscleGroupPickerScreen(
          // TODO localize
          title = if (alternateTo != null) "Add alternate" else "Add exercise",
          onContinue = { muscle ->
            val alternateQuery =
              if (alternateTo != null) "?alternateTo=${Uri.encode(alternateTo)}" else ""
            controller.navigate("add-exercise/$workoutId/$day/${Uri.encode(muscle)}$alternateQuery")
          },
          onClose = { controller.popBackStack() }
        )
      } else {
        controller.navigate("calendar")
      }
    }
    composable(
      "add-exercise/{workout}/{day}/{muscle}?alternateTo={alternateTo}",
      arguments = alternateToArg
    ) {
      val exerciseModel: ExerciseViewModel = hiltViewModel(it)
      val workoutModel: WorkoutViewModel = hiltViewModel(it)
      val userModel: UserViewModel = hiltViewModel(it)
      val workoutId = it.arguments?.getString("workout")
      val day = it.arguments?.getString("day")
      val muscle = it.arguments?.getString("muscle")?.let(Uri::decode)
      val alternateTo = it.arguments?.getString("alternateTo")?.let(Uri::decode)
      if (workoutId != null && day != null && muscle != null) {
        val localExercises by remember(muscle) { exerciseModel.exercisesByMuscle(muscle) }
          .collectAsStateWithLifecycle(initialValue = emptyList())
        val accessibleWorkouts by exerciseModel.accessibleWorkouts
          .collectAsStateWithLifecycle(initialValue = emptyList())
        // The plan list itself (accessibleWorkouts) only updates when this same paging refresh
        // runs - reusing it rather than a separate sync path keeps this screen's "refresh the
        // plan list" in lockstep with the drawer's.
        val workoutPlansPagingItems = workoutModel.workouts.collectAsLazyPagingItems()
        val currentEmail by userModel.userEmail.collectAsStateWithLifecycle(initialValue = null)
        // Reload accessibleWorkouts once sign-in actually completes, so newly-unlocked admin
        // plans show up without the user having to tap the refresh icon themselves.
        var signInClicked by remember { mutableStateOf(false) }
        LaunchedEffect(currentEmail) {
          if (signInClicked) {
            workoutPlansPagingItems.refresh()
          }
        }
        ExercisePickerList(
          muscle = muscle,
          // Exclude the plan being built itself - a custom plan is assembled from admin
          // content, so any local rows under its own name just duplicate an admin section.
          localExercisesByWorkout = localExercises
            .filter { it.workout != workoutId }
            .groupBy { it.workout },
          accessibleWorkouts = accessibleWorkouts,
          remoteExercisesByWorkout = exerciseModel.remoteExercisesByWorkout,
          loadingWorkouts = exerciseModel.loadingWorkouts,
          onLoadWorkout = { workout -> exerciseModel.loadRemoteExercises(workout, muscle) },
          onPick = { exercise ->
            if (alternateTo != null) {
              exerciseModel.addAlternateExercise(
                workoutId, day, alternateTo, exercise.id, exercise.description
              )
            } else {
              exerciseModel.addExercise(workoutId, day, exercise.id, exercise.description)
            }
            controller.getBackStackEntry("exercise/{workout}/{day}/{editing}")
              .savedStateHandle["justAddedExercise"] = exercise.name
            // Pop the whole add-exercise sub-flow at once, back to the day screen.
            controller.popBackStack("exercise/{workout}/{day}/{editing}", inclusive = false)
          },
          onBack = { controller.popBackStack() },
          onRefreshWorkouts = { workoutPlansPagingItems.refresh() },
          isRefreshingWorkouts = workoutPlansPagingItems.loadState.refresh is LoadState.Loading,
          authedEmail = currentEmail,
          onSignInSuccess = { response ->
            signInClicked = true
            when (val credential = response.credential) {
              is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                  userModel.handleSignIn(credential.data)
                } else {
                  Log.e("AddExercisePicker", "Unexpected type of credential")
                }
              }

              else -> Log.e("AddExercisePicker", "Unexpected type of credential")
            }
          },
          onSignInFailure = { Log.e("AddExercisePicker", "Sign in failed", it) },
          webClientId = userModel.googleWebClientId
        )
      } else {
        controller.navigate("calendar")
      }
    }
  }
}
