package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.exercise.input.WeightButtons
import com.litus_animae.refitted.ui.compose.state.SetHistory
import com.litus_animae.refitted.ui.compose.state.Weight
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import com.litus_animae.refitted.ui.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterialApi::class)
@FlowPreview
@Composable
fun Exercise(
  day: String, workoutId: String,
  editing: Boolean = false,
  exerciseModel: ExerciseViewModel = viewModel(),
  workoutModel: WorkoutViewModel = viewModel(),
  onAddExercise: () -> Unit = {},
  onAddAlternate: (ExerciseSet) -> Unit = {},
  scrollToExerciseName: String? = null
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
  var contextMenu by remember { mutableStateOf<@Composable RowScope.(Boolean) -> Unit>({}) }
  val (historyList, setHistoryList) = remember {
    mutableStateOf(SetHistory())
  }
  Scaffold(
    // navigationBars alone leaves a side-mounted camera cutout unhandled once rotated to
    // landscape — the two-pane exercise layout then splits content right up against it.
    contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    topBar = {
      val barTitle = "$title: $workoutId $dayWord $day"
      val showAddExercise = loadedWorkoutPlan?.isCustom == true && editing
      BoxWithConstraints {
        val textMeasurer = rememberTextMeasurer()
        val titleStyle = MaterialTheme.typography.h6
        val buttonStyle = MaterialTheme.typography.button
        val alternateLabel = stringResource(id = R.string.alternate)
        val density = LocalDensity.current
        val availablePx = with(density) { maxWidth.toPx() }
        // Navigation icon plus the add-exercise icon, both 48dp touch targets, and the
        // TextButton's own horizontal content padding.
        val fixedPx = with(density) {
          (48.dp + (if (showAddExercise) 48.dp else 0.dp) + 16.dp).toPx()
        }
        val titlePx = remember(barTitle, titleStyle) {
          textMeasurer.measure(barTitle, titleStyle).size.width
        }
        val alternatePx = remember(alternateLabel, buttonStyle) {
          textMeasurer.measure(alternateLabel, buttonStyle).size.width
        }
        val collapsed = titlePx + alternatePx + fixedPx > availablePx
        TopAppBar(
          title = { Text(barTitle) },
          windowInsets = AppBarDefaults.topAppBarWindowInsets.union(
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
          ),
          backgroundColor = MaterialTheme.colors.primary,
          actions = {
            if (showAddExercise) {
              IconButton(onAddExercise) {
                // TODO localize
                Icon(Icons.Default.Add, "add exercise")
              }
            }
            contextMenu(collapsed)
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
      }
    },
    scaffoldState = scaffoldState,
    drawerContent = { SetRecordList(history = historyList) }
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
      PagerExerciseView(exerciseModel,
        workoutPlan = loadedWorkoutPlan,
        contentPadding = it,
        setHistoryList = { setHistoryList(it) },
        setContextMenu = { contextMenu = it },
        onAlternateChange = { workoutModel.setGlobalIndexIfEnabled(loadedWorkoutPlan, it) },
        onStartEditWeight = {
          sheetWeight = it
          scaffoldScope.launch { sheetState.show() }
        },
        onSetSaved = { workoutModel.alignToDayIfUnaligned(loadedWorkoutPlan, day.toIntOrNull() ?: 1) },
        onOpenHistory = { scaffoldScope.launch { scaffoldState.drawerState.open() } },
        editing = editing,
        onAddExercise = onAddExercise,
        onAddAlternate = onAddAlternate,
        scrollToExerciseName = scrollToExerciseName)
    }
  }
}
