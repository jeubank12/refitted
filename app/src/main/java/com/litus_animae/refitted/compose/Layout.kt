package com.litus_animae.refitted.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.WorkoutPlan
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun Main(
    navigateToWorkoutDay: (WorkoutPlan, Int) -> Unit,
    model: WorkoutViewModel = viewModel()
) {
    val scaffoldState = rememberScaffoldState()
    val scaffoldScope = rememberCoroutineScope()
    val coroutineScope = rememberCoroutineScope()

    val selectedWorkoutPlan by model.currentWorkout.collectAsState(initial = model.savedStateLastWorkoutPlan)
    val savedSelectedPlanLoading = model.savedStateLoading
    val completedDaysLoading = model.completedDaysLoading

    val completedDays by model.completedDays.collectAsState(initial = emptyMap())

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
            Calendar(
                // TODO actual number of days
                selectedWorkoutPlan!!,
                completedDays
            ) { navigateToWorkoutDay(selectedWorkoutPlan!!, it) }
        }
    }
}

@FlowPreview
@Composable
fun Exercise(day: String, workoutId: String, model: ExerciseViewModel = viewModel()) {
    val title = stringResource(id = R.string.app_name)
    val dayWord = stringResource(id = R.string.day)
    val isLoading by model.isLoading.collectAsState()
    LaunchedEffect(day, workoutId) {
        model.loadExercises(day, workoutId)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("$title: $workoutId $dayWord $day") },
            backgroundColor = MaterialTheme.colors.primary
        )
    }) {
        if (isLoading) {
            Surface(Modifier.fillMaxSize()) {
                LoadingView()
            }
        } else {
            ExerciseDetail(model)
        }
    }
}
