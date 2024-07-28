package com.litus_animae.refitted.compose.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.AuthButton
import com.litus_animae.refitted.compose.util.LoadingView
import com.litus_animae.refitted.models.UserViewModel
import com.litus_animae.refitted.models.WorkoutPlan
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun Calendar(
  modifier: Modifier = Modifier,
  navigateToWorkoutDay: (WorkoutPlan, Int) -> Unit,
  workoutModel: WorkoutViewModel = viewModel(),
  userModel: UserViewModel = viewModel()
) {
  val scaffoldState = rememberScaffoldState()
  val scaffoldScope = rememberCoroutineScope()

  val selectedWorkoutPlan by workoutModel.currentWorkout.collectAsState(
    initial = workoutModel.savedStateLastWorkoutPlan,
    Dispatchers.IO
  )
  val savedSelectedPlanLoading = workoutModel.savedStateLoading
  val completedDaysLoading = workoutModel.completedDaysLoading

  val completedDays by workoutModel.completedDays.collectAsState(
    initial = emptyMap(),
    Dispatchers.IO
  )

  Scaffold(
    modifier,
    scaffoldState = scaffoldState,
    topBar = {
      TopAppBar(
        title = {
          val appName = stringResource(id = R.string.app_name)
          if (selectedWorkoutPlan != null) Text(selectedWorkoutPlan!!.workout)
          else Text(appName)
        },
        backgroundColor = MaterialTheme.colors.primary,
        navigationIcon = {
          Icon(
            Icons.Default.Menu,
            // TODO localize
            "menu",
            modifier = Modifier
              .clickable {
                scaffoldScope.launch {
                  if (scaffoldState.drawerState.isClosed) scaffoldState.drawerState.open()
                  else scaffoldState.drawerState.close()
                }
              }
              .padding(start = 10.dp))
        },
        actions = {
          if (selectedWorkoutPlan != null) {
            val (expanded, setExpanded) = rememberSaveable { mutableStateOf(false) }
            val (alerted, setAlerted) = rememberSaveable { mutableStateOf(false) }
            // TODO localize
            Icon(Icons.Default.MoreVert, "workout menu",
              modifier = Modifier
                .clickable { setExpanded(!expanded) }
                .padding(end = 10.dp))
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { setExpanded(false) }) {
              Text("Reset workout",
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    setAlerted(true)
                    setExpanded(false)
                  }
                  .padding(start = 5.dp, end = 15.dp)
                  .padding(vertical = 5.dp))
            }
            if (alerted) {
              AlertDialog(onDismissRequest = { setAlerted(false) },
                // TODO localize
                title = { Text("Reset Workout Completion") },
                text = { Text("This will reset your completed days. Are you sure? (This does not remove records of your previous exercise sets") },
                confirmButton = {
                  Button(onClick = {
                    workoutModel.resetWorkoutCompletion(
                      selectedWorkoutPlan!!
                    )
                    setAlerted(false)
                  }) {
                    Text("Yes")
                  }
                },
                dismissButton = {
                  Button(onClick = { setAlerted(false) }) {
                    Text("No")
                  }
                })
            }
          }
        }
      )
    },
    drawerShape = MaterialTheme.shapes.medium,
    drawerContent = {
      val workoutPlanPagingItems = workoutModel.workouts.collectAsLazyPagingItems()
      val workoutPlanError = workoutModel.workoutError
      LaunchedEffect(workoutPlanError) {
        if (workoutPlanError != null)
          scaffoldState.snackbarHostState.showSnackbar(
            workoutPlanError,
            duration = SnackbarDuration.Indefinite
          )
      }
      val lastRefresh by workoutModel.workoutsLastRefreshed.collectAsState(initial = "")
      WorkoutPlanMenu(Modifier.weight(1f), lastRefresh, workoutPlanPagingItems, workoutPlanError) {
        scaffoldScope.launch { scaffoldState.drawerState.close() }
        workoutModel.loadWorkoutDaysCompleted(it)
      }
      Row(Modifier.padding(10.dp)) {
        val currentEmail by userModel.userEmail.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        var signInClicked by remember { mutableStateOf(false) }

        LaunchedEffect(currentEmail) {
          if (currentEmail != null && signInClicked) {
            workoutPlanPagingItems.refresh()
          }
        }

        AuthButton(
          Modifier.fillMaxWidth(),
          handleAuthSuccess = {
            signInClicked = true
            userModel.handleSignIn(it)
          }, handleAuthFailure = {
            coroutineScope.launch {
              it.message?.let { it1 -> scaffoldState.snackbarHostState.showSnackbar(it1) }
            }
          },
          handleDeAuth = { userModel.handleSignOut() },
          authedEmail = currentEmail)
      }
    }) { contentPadding ->
    if (savedSelectedPlanLoading || (selectedWorkoutPlan != null && completedDaysLoading)) {
      Surface(
        Modifier
          .fillMaxSize()
          .padding(contentPadding)
      ) {
        LoadingView()
      }
    } else if (selectedWorkoutPlan == null) {
      // TODO instruction page
      Row(
        Modifier
          .padding(contentPadding)
          .padding(start = 10.dp, top = 10.dp)
          .fillMaxWidth()
      ) {
        Text("Open the menu to pick a workout")
      }
    } else {
      WorkoutCalendar(
        selectedWorkoutPlan!!,
        completedDays,
        contentPadding = contentPadding
      ) {
        navigateToWorkoutDay(selectedWorkoutPlan!!, it)
        workoutModel.setLastViewedDay(selectedWorkoutPlan!!, it)
      }
    }
  }
}
