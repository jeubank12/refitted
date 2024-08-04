package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.exercise.input.WeightButtons
import com.litus_animae.refitted.compose.state.Weight
import com.litus_animae.refitted.compose.util.LoadingView
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.SetRecord
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterialApi::class)
@FlowPreview
@Composable
fun Exercise(
  day: String, workoutId: String,
  exerciseModel: ExerciseViewModel = viewModel(),
  workoutModel: WorkoutViewModel = viewModel()
) {
  val title = stringResource(id = R.string.app_name)
  val dayWord = stringResource(id = R.string.day)
  val scaffoldState = rememberScaffoldState()
  val scaffoldScope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

  val loadedWorkoutPlan by workoutModel.currentWorkout.collectAsState(
    initial = workoutModel.savedStateLastWorkoutPlan,
    Dispatchers.IO
  )

  LaunchedEffect(day, workoutId) {
    exerciseModel.loadExercises(day, workoutId)
  }
  var contextMenu by remember { mutableStateOf<@Composable RowScope.() -> Unit>({}) }
  val (historyList, setHistoryList) = remember {
    mutableStateOf(emptyFlow<PagingData<SetRecord>>())
  }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("$title: $workoutId $dayWord $day") },
        backgroundColor = MaterialTheme.colors.primary,
        actions = {
          contextMenu()
        },
        navigationIcon = {
          Icon(
            Icons.Default.History,
            // TODO localize
            "history",
            modifier = Modifier
              .clickable {
                scaffoldScope.launch {
                  if (scaffoldState.drawerState.isClosed) scaffoldState.drawerState.open()
                  else scaffoldState.drawerState.close()
                }
              }
              .padding(start = 10.dp))
        }
      )
    },
    scaffoldState = scaffoldState,
    drawerContent = { SetRecordList(flow = historyList) }
  ) {
    var sheetWeight by remember { mutableStateOf(Weight(0.0)) }
    ModalBottomSheetLayout(
      sheetContent = { Box(Modifier.padding(top = 10.dp, bottom = 10.dp)){WeightButtons(sheetWeight)} },
      sheetState = sheetState
    ) {
      ExerciseView(exerciseModel,
        workoutPlan = loadedWorkoutPlan,
        contentPadding = it,
        setHistoryList = { setHistoryList(it) },
        setContextMenu = { contextMenu = it },
        onAlternateChange = { workoutModel.setGlobalIndexIfEnabled(loadedWorkoutPlan, it) },
        onStartEditWeight = {
          sheetWeight = it
          scaffoldScope.launch { sheetState.show() }
        })
    }
  }
}
