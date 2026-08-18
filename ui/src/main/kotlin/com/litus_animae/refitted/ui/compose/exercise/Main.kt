package com.litus_animae.refitted.ui.compose.exercise

import android.app.Activity
import android.util.Log
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.credentials.CustomCredential
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.exercise.add.AddExercisePanel
import com.litus_animae.refitted.ui.compose.exercise.input.WeightButtons
import com.litus_animae.refitted.ui.compose.state.SetHistory
import com.litus_animae.refitted.ui.compose.state.Weight
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import com.litus_animae.refitted.ui.models.UserViewModel
import com.litus_animae.refitted.ui.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)
@FlowPreview
@Composable
fun Exercise(
  day: String, workoutId: String,
  editing: Boolean = false,
  exerciseModel: ExerciseViewModel = viewModel(),
  workoutModel: WorkoutViewModel = viewModel(),
  userModel: UserViewModel = viewModel()
) {
  val title = stringResource(id = R.string.app_name)
  val dayWord = stringResource(id = R.string.day)
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scaffoldScope = rememberCoroutineScope()
  var showWeightSheet by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState()
  var showAddExerciseSheet by remember { mutableStateOf(false) }
  val addExerciseSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
  // Which existing step (if any) the picked exercise should become an alternate of - null
  // means "add exercise", non-null means "add alternate" for that step.
  var alternateToStep by rememberSaveable { mutableStateOf<String?>(null) }
  // Lands the pager on the exercise just added - consumed once by PagerExerciseView.
  var scrollToExerciseName by rememberSaveable { mutableStateOf<String?>(null) }

  // Folding-feature awareness for foldables opened to a book/tabletop posture - the activity is
  // the app's single Compose host, so LocalContext.current is always safe to cast here.
  val activity = LocalContext.current as Activity
  val windowInfoTracker = remember(activity) { WindowInfoTracker.getOrCreate(activity) }
  val windowLayoutInfo by remember(windowInfoTracker, activity) {
    windowInfoTracker.windowLayoutInfo(activity)
  }.collectAsState(initial = WindowLayoutInfo(emptyList()))
  val foldingFeature = windowLayoutInfo.displayFeatures
    .filterIsInstance<FoldingFeature>()
    .firstOrNull()
  // FLAT means the device is unfolded all the way open (as opposed to HALF_OPENED book/tabletop
  // posture) - there's enough width there to show history as a permanent side pane rather than
  // a dismissible drawer overlay fighting the user for the same screen real estate.
  val historyAsSidePane = foldingFeature?.state == FoldingFeature.State.FLAT

  var sheetWeight by remember { mutableStateOf(Weight(0.0)) }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = { ModalDrawerSheet { SetRecordList(history = historyList) } }
  ) {
    Scaffold(
      // navigationBars alone leaves a side-mounted camera cutout unhandled once rotated to
      // landscape — the two-pane exercise layout then splits content right up against it.
      contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
      topBar = {
        val barTitle = "$title: $workoutId $dayWord $day"
        val showAddExercise = loadedWorkoutPlan?.isCustom == true && editing
        BoxWithConstraints {
          val textMeasurer = rememberTextMeasurer()
          val titleStyle = MaterialTheme.typography.titleLarge
          val buttonStyle = MaterialTheme.typography.labelLarge
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
            windowInsets = TopAppBarDefaults.windowInsets.union(
              WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
            ),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
            actions = {
              // Expanded, the labelled action reads as a peer of + and sits ahead of it;
              // collapsed, it is an overflow menu and belongs last instead.
              if (!collapsed) contextMenu(false)
              if (showAddExercise) {
                IconButton({
                  alternateToStep = null
                  showAddExerciseSheet = true
                }) {
                  // TODO localize
                  Icon(Icons.Default.Add, "add exercise")
                }
              }
              if (collapsed) contextMenu(true)
            },
            navigationIcon = {
              IconButton({
                scaffoldScope.launch {
                  if (drawerState.isClosed) drawerState.open()
                  else drawerState.close()
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
      }
    ) {
      PagerExerciseView(exerciseModel,
        workoutPlan = loadedWorkoutPlan,
        contentPadding = it,
        foldingFeature = foldingFeature,
        setHistoryList = { setHistoryList(it) },
        setContextMenu = { contextMenu = it },
        onAlternateChange = { workoutModel.setGlobalIndexIfEnabled(loadedWorkoutPlan, it) },
        onStartEditWeight = {
          sheetWeight = it
          showWeightSheet = true
        },
        onSetSaved = { workoutModel.alignToDayIfUnaligned(loadedWorkoutPlan, day.toIntOrNull() ?: 1) },
        onOpenHistory = { scaffoldScope.launch { drawerState.open() } },
        editing = editing,
        onAddExercise = {
          alternateToStep = null
          showAddExerciseSheet = true
        },
        onAddAlternate = { set ->
          alternateToStep = set.primaryStep
          showAddExerciseSheet = true
        },
        scrollToExerciseName = scrollToExerciseName)

      if (showWeightSheet) {
        ModalBottomSheet(
          onDismissRequest = { showWeightSheet = false },
          sheetState = sheetState
        ) {
          Box(Modifier.padding(top = 10.dp, bottom = 10.dp)) {
            WeightButtons(sheetWeight)
          }
        }
      }

      if (showAddExerciseSheet) {
        ModalBottomSheet(
          onDismissRequest = { showAddExerciseSheet = false },
          sheetState = addExerciseSheetState
        ) {
          // M3's ModalBottomSheet is only composed while showAddExerciseSheet is true, unlike
          // M2's ModalBottomSheetLayout (whose sheetContent composed continuously even hidden,
          // see ui/CLAUDE.md's old gotcha) - no manual visibility gate needed here anymore.
          val selectedMuscle by exerciseModel.selectedMuscle.collectAsStateWithLifecycle()
          val localExercises by remember(selectedMuscle) {
            exerciseModel.exercisesByMuscle(selectedMuscle)
          }.collectAsStateWithLifecycle(initialValue = emptyList())
          val accessibleWorkouts by exerciseModel.accessibleWorkouts
            .collectAsStateWithLifecycle(initialValue = emptyList())
          // The plan list itself (accessibleWorkouts) only updates when this same paging refresh
          // runs - reusing it rather than a separate sync path keeps this screen's "refresh the
          // plan list" in lockstep with the drawer's.
          val workoutPlansPagingItems = workoutModel.workouts.collectAsLazyPagingItems()
          val currentEmail by userModel.userEmail.collectAsStateWithLifecycle(initialValue = null)
          // Reload accessibleWorkouts once sign-in actually completes, so newly-unlocked admin
          // plans show up without the user having to tap the refresh icon themselves.
          var signInClicked by remember { mutableStateOf(false) }
          LaunchedEffect(currentEmail) {
            if (signInClicked) {
              workoutPlansPagingItems.refresh()
            }
          }
          AddExercisePanel(
            title = if (alternateToStep != null) "Add alternate" else "Add exercise",
            muscle = selectedMuscle,
            onMuscleSelected = exerciseModel::selectMuscle,
            // Exclude the plan being built itself - a custom plan is assembled from admin
            // content, so any local rows under its own name just duplicate an admin section.
            localExercisesByWorkout = localExercises
              .filter { it.workout != workoutId }
              .groupBy { it.workout },
            accessibleWorkouts = accessibleWorkouts,
            remoteExercisesByWorkout = exerciseModel.remoteExercisesByWorkout,
            loadingWorkouts = exerciseModel.loadingWorkouts,
            onLoadWorkout = { workout -> exerciseModel.loadRemoteExercises(workout, selectedMuscle) },
            onPick = { exercise ->
              val baseStep = alternateToStep
              if (baseStep != null) {
                exerciseModel.addAlternateExercise(
                  workoutId, day, baseStep, exercise.id, exercise.description
                )
              } else {
                exerciseModel.addExercise(workoutId, day, exercise.id, exercise.description)
              }
              scrollToExerciseName = exercise.name
              scaffoldScope.launch { addExerciseSheetState.hide() }.invokeOnCompletion {
                if (!addExerciseSheetState.isVisible) showAddExerciseSheet = false
              }
            },
            onClose = {
              scaffoldScope.launch { addExerciseSheetState.hide() }.invokeOnCompletion {
                if (!addExerciseSheetState.isVisible) showAddExerciseSheet = false
              }
            },
            onRefreshWorkouts = { workoutPlansPagingItems.refresh() },
            isRefreshingWorkouts = workoutPlansPagingItems.loadState.refresh is LoadState.Loading,
            authedEmail = currentEmail,
            onSignInSuccess = { response ->
              signInClicked = true
              when (val credential = response.credential) {
                is CustomCredential -> {
                  if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    userModel.handleSignIn(credential.data)
                  } else {
                    Log.e("Exercise", "Unexpected type of credential")
                  }
                }

                else -> Log.e("Exercise", "Unexpected type of credential")
              }
            },
            onSignInFailure = { Log.e("Exercise", "Sign in failed", it) },
            webClientId = userModel.googleWebClientId
          )
        }
      }
    }
  }
}
