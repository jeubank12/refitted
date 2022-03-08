package com.litus_animae.refitted.compose.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.litus_animae.refitted.compose.LoadingView
import com.litus_animae.refitted.compose.WorkoutCalendar
import com.litus_animae.refitted.compose.WorkoutPlanMenu
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.exercise.ExerciseDetails
import com.litus_animae.refitted.models.ExerciseViewModel
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
    val coroutineScope = rememberCoroutineScope()

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
                    selectedWorkoutPlan?.let { Text(it.workout) } ?: Text("Refitted")
                },
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
                backgroundColor = MaterialTheme.colors.primary
            )
        },
        drawerShape = MaterialTheme.shapes.medium,
        drawerContent = {
            val workoutPlanPagingItems = model.workouts.collectAsLazyPagingItems()
            WorkoutPlanMenu(workoutPlanPagingItems) {
                scaffoldScope.launch { scaffoldState.drawerState.close() }
                coroutineScope.launch { model.loadWorkoutDaysCompleted(it) }
            }
        }) {
        if (savedSelectedPlanLoading || completedDaysLoading) {
            Surface(Modifier.fillMaxSize()) {
                LoadingView()
            }
        } else if (selectedWorkoutPlan == null) {
            // TODO instruction page
            Row(
                Modifier
                    .padding(start = 10.dp, top = 10.dp)
                    .fillMaxWidth()
            ) {
                Text("Open the menu to pick a workout")
            }
        } else {
            WorkoutCalendar(
                selectedWorkoutPlan!!,
                completedDays
            ) {
                navigateToWorkoutDay(selectedWorkoutPlan!!, it)
                coroutineScope.launch { model.setLastViewedDay(selectedWorkoutPlan!!, it) }
            }
        }
    }
}
