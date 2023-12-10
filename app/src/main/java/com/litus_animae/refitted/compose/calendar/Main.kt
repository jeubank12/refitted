package com.litus_animae.refitted.compose.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.util.LoadingView
import com.litus_animae.refitted.models.WorkoutPlan
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun Calendar(
  navigateToWorkoutDay: (WorkoutPlan, Int) -> Unit,
  model: WorkoutViewModel = viewModel(),
) {
  val scaffoldState = rememberScaffoldState()
  val scaffoldScope = rememberCoroutineScope()

  val selectedWorkoutPlan by model.currentWorkout.collectAsState(
    initial = model.savedStateLastWorkoutPlan,
    Dispatchers.IO
  )
  val savedSelectedPlanLoading = model.savedStateLoading
  val completedDaysLoading = model.completedDaysLoading

  val completedDays by model.completedDays.collectAsState(initial = emptyMap(), Dispatchers.IO)

  Scaffold(
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
                    model.resetWorkoutCompletion(
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
      val workoutPlanPagingItems = model.workouts.collectAsLazyPagingItems()
      val workoutPlanError = model.workoutError
      LaunchedEffect(workoutPlanError){
        if (workoutPlanError != null)
          scaffoldState.snackbarHostState.showSnackbar(workoutPlanError, duration = SnackbarDuration.Indefinite)
      }
      val lastRefresh by model.workoutsLastRefreshed.collectAsState(initial = "")
      WorkoutPlanMenu(lastRefresh, workoutPlanPagingItems, workoutPlanError) {
        scaffoldScope.launch { scaffoldState.drawerState.close() }
        model.loadWorkoutDaysCompleted(it)
      }
    }) {contentPadding ->
    if (savedSelectedPlanLoading || (selectedWorkoutPlan != null && completedDaysLoading)) {
      Surface(Modifier.fillMaxSize().padding(contentPadding)) {
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
        model.setLastViewedDay(selectedWorkoutPlan!!, it)
      }
    }
  }
}
