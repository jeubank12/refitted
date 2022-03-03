package com.litus_animae.refitted.compose

import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

@Composable
fun Main(
    navigateToWorkoutDay: (WorkoutPlan, Int) -> Unit,
    model: WorkoutViewModel = viewModel()
) {
    val savedSelectedWorkout = model.savedStateLastWorkoutPlan
    var selectedWorkoutPlan by remember { mutableStateOf(savedSelectedWorkout) }

    val completedDays by model.completedDays.collectAsState(initial = emptyMap())
    val scaffoldState = rememberScaffoldState()
    val scaffoldScope = rememberCoroutineScope()
    LaunchedEffect(selectedWorkoutPlan?.workout) {
        selectedWorkoutPlan?.workout?.let { model.loadWorkoutDaysCompleted(it) }
    }

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
                selectedWorkoutPlan = it
                scaffoldScope.launch { scaffoldState.drawerState.close() }
            }
        }) {
        if (selectedWorkoutPlan == null) {
            // TODO instruction page
            Text("Open the menu to pick a workout")
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
            LoadingView()
        } else {
            ExerciseDetail(model)
        }
    }
}
