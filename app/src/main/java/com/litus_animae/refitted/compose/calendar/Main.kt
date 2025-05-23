package com.litus_animae.refitted.compose.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.AlertDialog
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.AuthButton
import com.litus_animae.refitted.compose.Changelog
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

  val shouldShowChangelog by userModel.shouldShowChangelog()
    .collectAsStateWithLifecycle(initialValue = false)
  if (shouldShowChangelog) {
    Changelog { userModel.setChangelogShown() }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.navigationBars,
    modifier,
    scaffoldState = scaffoldState,
    topBar = {
      TopAppBar(
        title = {
          val appName = stringResource(id = R.string.app_name)
          if (selectedWorkoutPlan != null) Text(selectedWorkoutPlan!!.workout)
          else Text(appName)
        },
        windowInsets = AppBarDefaults.topAppBarWindowInsets,
        backgroundColor = MaterialTheme.colors.primary,
        navigationIcon = {
          IconButton(
            {
              scaffoldScope.launch {
                if (scaffoldState.drawerState.isClosed) scaffoldState.drawerState.open()
                else scaffoldState.drawerState.close()
              }
            }
          ) {
            Icon(
              Icons.Default.Menu,
              // TODO localize
              "menu"
            )
          }
        },
        actions = {
          if (selectedWorkoutPlan != null) {
            val (expanded, setExpanded) = rememberSaveable { mutableStateOf(false) }
            val (alerted, setAlerted) = rememberSaveable { mutableStateOf(false) }
            IconButton({ setExpanded(!expanded) }) {
              // TODO localize
              Icon(Icons.Default.MoreVert, "workout menu")
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { setExpanded(false) }) {
              Text(
                "Reset workout",
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    setAlerted(true)
                    setExpanded(false)
                  }
                  .padding(start = 5.dp, end = 15.dp)
                  .padding(vertical = 5.dp))
              val isAdmin by userModel.userIsAdmin.collectAsStateWithLifecycle(initialValue = false)
              if (isAdmin) {
                Text(
                  "Crash",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      throw RuntimeException("Test Crash")
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
              }
            }
            if (alerted) {
              AlertDialog(
                onDismissRequest = { setAlerted(false) },
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
      val lastRefresh by workoutModel.workoutsLastRefreshed.collectAsStateWithLifecycle(initialValue = "")
      WorkoutPlanMenu(
        Modifier.weight(1f),
        lastRefresh,
        workoutPlanPagingItems,
        workoutPlanError
      ) {
        scaffoldScope.launch { scaffoldState.drawerState.close() }
        workoutModel.loadWorkoutDaysCompleted(it)
      }
      Row(
        Modifier
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(start = 10.dp, end = 10.dp, top = 10.dp)
      ) {
        val currentEmail by userModel.userEmail.collectAsStateWithLifecycle(initialValue = null)
        val coroutineScope = rememberCoroutineScope()
        var signInClicked by remember { mutableStateOf(false) }

        LaunchedEffect(currentEmail) {
          if (signInClicked) {
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
          authedEmail = currentEmail
        )
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
