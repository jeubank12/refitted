package com.litus_animae.refitted.compose

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
import androidx.paging.compose.items
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.exercise.ExerciseView
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

@OptIn(ExperimentalCoroutinesApi::class)
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
    ExerciseView(exerciseModel,
      workoutPlan = loadedWorkoutPlan,
      contentPadding = it,
      setHistoryList = { setHistoryList(it) },
      setContextMenu = { contextMenu = it },
      onAlternateChange = { workoutModel.setGlobalIndexIfEnabled(loadedWorkoutPlan, it) })
  }
}

@Composable
private fun SetRecordList(flow: Flow<PagingData<SetRecord>>) {
  val items = flow.collectAsLazyPagingItems()
  LazyColumn {
    item {
      Row(
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colors.primary)
          .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // TODO localize
        Text(
          "Set History", style = MaterialTheme.typography.h6, color = contentColorFor(
            backgroundColor = MaterialTheme.colors.primary
          )
        )
        Icon(
          Icons.Default.Refresh,
          // TODO localize
          "refresh",
          modifier = Modifier
            .clickable {
              items.refresh()
            }
            .padding(start = 10.dp, end = 10.dp),
          tint = contentColorFor(backgroundColor = MaterialTheme.colors.primary))
      }
    }

    if (items.loadState.refresh is LoadState.Loading) {
      item {
        Row(Modifier.fillMaxWidth()) {
          LoadingView()
        }
      }
    } else {
      val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
      item {
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 15.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            stringResource(id = R.string.date),
            style = MaterialTheme.typography.button
          )
          Text(
            stringResource(id = R.string.reps_label),
            style = MaterialTheme.typography.button
          )
          val weightLabel = stringResource(id = R.string.weight_label)
          val weightUnits = stringResource(id = R.string.lbs)
          Text(
            "$weightLabel ($weightUnits)",
            style = MaterialTheme.typography.button
          )
        }
      }

      items(items) { record ->
        if (record != null) {
          Row(
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              dateFormat.format(record.completed),
              style = MaterialTheme.typography.button
            )
            Text(
              record.reps.toString(),
              style = MaterialTheme.typography.button
            )
            Text(
              String.format("%.1f", record.weight),
              style = MaterialTheme.typography.button
            )
          }
          Divider()
        }
      }
    }
  }
}
