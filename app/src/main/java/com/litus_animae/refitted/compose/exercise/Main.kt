package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.exercise.input.WeightButtons
import com.litus_animae.refitted.compose.state.Weight
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.SetRecord
import com.litus_animae.refitted.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

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
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        title = { Text("$title: $workoutId $dayWord $day") },
        windowInsets = AppBarDefaults.topAppBarWindowInsets,
        backgroundColor = MaterialTheme.colors.primary,
        actions = {
          contextMenu()
        },
        navigationIcon = {
          IconButton({
            scaffoldScope.launch {
              if (scaffoldState.drawerState.isClosed) scaffoldState.drawerState.open()
              else scaffoldState.drawerState.close()
            }
          }) {
            Icon(
              Icons.Default.History,
              // TODO localize
              "history"
            )
          }
        }
      )
    },
    scaffoldState = scaffoldState,
    drawerContent = { SetRecordList(flow = historyList) }
  ) {
    var sheetWeight by remember { mutableStateOf(Weight(0.0)) }
    ModalBottomSheetLayout(
      sheetContent = {
        Box(Modifier.padding(top = 10.dp, bottom = 10.dp)) {
          WeightButtons(
            sheetWeight
          )
        }
      },
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
