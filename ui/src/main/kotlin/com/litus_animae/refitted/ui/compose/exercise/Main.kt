package com.litus_animae.refitted.ui.compose.exercise

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AdaptStrategy
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.LevitatedPaneScrim
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.credentials.CustomCredential
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.ui.compose.util.cutoutAffects
import com.litus_animae.refitted.ui.compose.util.rememberDisplayCutoutBoundingRects
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.litus_animae.refitted.ui.compose.exercise.add.AddExercisePanel
import com.litus_animae.refitted.ui.compose.util.appBarColors
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import com.litus_animae.refitted.ui.models.UserViewModel
import com.litus_animae.refitted.ui.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@FlowPreview
@Composable
fun Exercise(
  day: String, workoutId: String,
  editing: Boolean = false,
  exerciseModel: ExerciseViewModel = viewModel(),
  workoutModel: WorkoutViewModel = viewModel(),
  userModel: UserViewModel = viewModel()
) {
  val scaffoldScope = rememberCoroutineScope()

  val loadedWorkoutPlan by workoutModel.currentWorkout.collectAsState(
    initial = workoutModel.savedStateLastWorkoutPlan,
    Dispatchers.IO
  )

  LaunchedEffect(day, workoutId) {
    exerciseModel.loadExercises(day, workoutId)
  }
  val historyList by exerciseModel.setHistory.collectAsStateWithLifecycle()
  // True once the user explicitly opens history at Compact width - the pager is always the
  // default/home screen, this is only ever set by an explicit tap.
  var historyFocused by rememberSaveable { mutableStateOf(false) }
  // Whether the add-exercise picker is showing at all, and which existing step (if any) the
  // picked exercise should become an alternate of - null means "add exercise", non-null means
  // "add alternate" for that step.
  var showAddExercisePicker by rememberSaveable { mutableStateOf(false) }
  var alternateToStep by rememberSaveable { mutableStateOf<String?>(null) }
  // Lands the pager on the exercise just added - consumed once by PagerExerciseView.
  var scrollToExerciseName by rememberSaveable { mutableStateOf<String?>(null) }

  val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
  val directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(windowAdaptiveInfo)
    .let { it.copy(horizontalPartitionSpacerSize = 0.dp) }
  // maxHorizontalPartitions is width-only - a landscape phone is comfortably Medium+ width but
  // short on height, so without this check both panes would show side by side and squeeze every
  // card down to a quarter of the screen width. Require enough height too, same threshold the
  // (deprecated but still present) WindowHeightSizeClass.MEDIUM cutoff used.
  val bothPanesFit = directive.maxHorizontalPartitions >= 2 &&
    windowAdaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(480)
  // The add-exercise picker only uses the Extra/Levitate role at Medium+ (a floating pane with
  // a scrim, docked over both other panes) - at Compact it's the existing full-screen
  // ModalBottomSheet instead, so Extra is never the destination there.
  val focusedRole = when {
    showAddExercisePicker && bothPanesFit -> SupportingPaneScaffoldRole.Extra
    historyFocused -> SupportingPaneScaffoldRole.Supporting
    else -> SupportingPaneScaffoldRole.Main
  }
  val scaffoldValue = calculateThreePaneScaffoldValue(
    // Must agree with bothPanesFit - that's what showHistoryButton/showBackButton/the
    // add-exercise picker's sheet-vs-pane choice are keyed off, so if this still allowed 2
    // partitions on a short-height window, both panes would render anyway while those flags
    // believed only one was showing (e.g. a back button with no pane transition to go back to).
    maxHorizontalPartitions = if (bothPanesFit) directive.maxHorizontalPartitions else 1,
    // Supporting defaults to AdaptStrategy.Reflow(Main), which bleeds its content into Main's
    // pane when hidden instead of just disappearing - Hide is what a drawer-like on/off pane
    // needs. Extra defaults to Hide too - Levitate is what turns it into a floating overlay.
    adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(
      supportingPaneAdaptStrategy = AdaptStrategy.Hide,
      extraPaneAdaptStrategy = AdaptStrategy.Levitate(
        scrim = { LevitatedPaneScrim(onClick = { showAddExercisePicker = false }) }
      )
    ),
    currentDestination = ThreePaneScaffoldDestinationItem<Nothing>(pane = focusedRole)
  )
  val paneScaffoldState = remember { MutableThreePaneScaffoldState(scaffoldValue) }
  LaunchedEffect(scaffoldValue) { paneScaffoldState.animateTo(scaffoldValue) }

  BackHandler(enabled = scaffoldValue[SupportingPaneScaffoldRole.Main] == PaneAdaptedValue.Hidden) {
    historyFocused = false
  }

  // Bounds-based, not edge-based - a cutout is compared against each pane's own measured window
  // bounds rather than assumed from which screen edge a role owns, so it correctly resolves to
  // "unaffected" even for cutouts WindowInsetsSides can't attribute to a side (e.g. a top-mounted
  // punch-hole). null until first layout - cutoutAffects treats that as conservatively affected.
  val cutoutRects = rememberDisplayCutoutBoundingRects()
  var mainPaneBounds by remember { mutableStateOf<Rect?>(null) }
  var supportingPaneBounds by remember { mutableStateOf<Rect?>(null) }
  val mainAffectedByCutout = cutoutAffects(mainPaneBounds, cutoutRects)
  val supportingAffectedByCutout = cutoutAffects(supportingPaneBounds, cutoutRects)

  Box(
    Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    SupportingPaneScaffold(
      directive = directive,
      scaffoldState = paneScaffoldState,
      mainPane = {
        AnimatedPane(Modifier.onGloballyPositioned { mainPaneBounds = it.boundsInWindow() }) {
          ExerciseMainPane(
            day = day,
            workoutId = workoutId,
            editing = editing,
            loadedWorkoutPlan = loadedWorkoutPlan,
            exerciseModel = exerciseModel,
            setHistoryList = exerciseModel::setHistoryList,
            onAlternateChange = { workoutModel.setGlobalIndexIfEnabled(loadedWorkoutPlan, it) },
            onSetSaved = { workoutModel.alignToDayIfUnaligned(loadedWorkoutPlan, day.toIntOrNull() ?: 1) },
            showHistoryButton = !bothPanesFit,
            onOpenHistory = { historyFocused = true },
            onAddExercise = {
              alternateToStep = null
              showAddExercisePicker = true
            },
            onAddAlternate = { set ->
              alternateToStep = set.primaryStep
              showAddExercisePicker = true
            },
            scrollToExerciseName = scrollToExerciseName,
            affectedByCutout = mainAffectedByCutout
          )
        }
      },
      supportingPane = {
        AnimatedPane(Modifier.onGloballyPositioned { supportingPaneBounds = it.boundsInWindow() }) {
          SetRecordList(
            history = historyList,
            showBackButton = !bothPanesFit,
            affectedByCutout = supportingAffectedByCutout,
            onBack = { historyFocused = false },
            onUpdateRecord = exerciseModel::updateSetRecord,
            onDeleteRecord = exerciseModel::deleteSetRecord
          )
        }
      },
      extraPane = {
        AnimatedPane {
          AddExercisePickerContent(
            title = if (alternateToStep != null) "Add alternate" else "Add exercise",
            workoutId = workoutId,
            day = day,
            alternateToStep = alternateToStep,
            exerciseModel = exerciseModel,
            workoutModel = workoutModel,
            userModel = userModel,
            onPicked = { name ->
              scrollToExerciseName = name
              showAddExercisePicker = false
            },
            onClose = { showAddExercisePicker = false },
            // The docked/Levitate presentation is a small box centered over the scaffold - it
            // never reaches a physical screen edge, so it shouldn't reserve display-cutout space.
            edgeToEdge = false,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    )
  }

  if (showAddExercisePicker && !bothPanesFit) {
    val addExerciseSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Matches the sheet's own rounded-corner surface (and drag handle) to AddExerciseList's blue
    // TopAppBar - otherwise the sheet's default light containerColor peeks out above/around the
    // handle before the app bar starts, reading as a background-colored gap at the top of the pane.
    val addExerciseSheetColors = appBarColors()
    // ModalBottomSheet itself caps at BottomSheetDefaults.SheetMaxWidth (640dp) and centers
    // itself as a dialog-like card on anything wider - e.g. a landscape phone, whose reduced
    // height forces this sheet path despite ample width. A centered card doesn't reach a screen
    // edge either, same as the docked pane above, so it shouldn't reserve display-cutout space.
    val sheetEdgeToEdge = LocalConfiguration.current.screenWidthDp.dp < BottomSheetDefaults.SheetMaxWidth
    ModalBottomSheet(
      onDismissRequest = { showAddExercisePicker = false },
      sheetState = addExerciseSheetState,
      containerColor = addExerciseSheetColors.containerColor,
      dragHandle = {
        BottomSheetDefaults.DragHandle(color = addExerciseSheetColors.titleContentColor)
      }
    ) {
      AddExercisePickerContent(
        title = if (alternateToStep != null) "Add alternate" else "Add exercise",
        workoutId = workoutId,
        day = day,
        alternateToStep = alternateToStep,
        exerciseModel = exerciseModel,
        workoutModel = workoutModel,
        userModel = userModel,
        onPicked = { name ->
          scrollToExerciseName = name
          scaffoldScope.launch { addExerciseSheetState.hide() }.invokeOnCompletion {
            if (!addExerciseSheetState.isVisible) showAddExercisePicker = false
          }
        },
        onClose = {
          scaffoldScope.launch { addExerciseSheetState.hide() }.invokeOnCompletion {
            if (!addExerciseSheetState.isVisible) showAddExercisePicker = false
          }
        },
        edgeToEdge = sheetEdgeToEdge
      )
    }
  }
}

/**
 * Shared wiring for [AddExercisePanel] - identical whether it's shown as a full-screen
 * [ModalBottomSheet] (Compact) or as the Extra pane's floating/[AdaptStrategy.Levitate] content
 * (Medium+); only [onPicked]/[onClose]/[edgeToEdge] differ, since dismissing each host works
 * differently and only the sheet host actually reaches the screen edges.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
private fun AddExercisePickerContent(
  title: String,
  workoutId: String,
  day: String,
  alternateToStep: String?,
  edgeToEdge: Boolean,
  exerciseModel: ExerciseViewModel,
  workoutModel: WorkoutViewModel,
  userModel: UserViewModel,
  onPicked: (exerciseName: String?) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedMuscle by exerciseModel.selectedMuscle.collectAsStateWithLifecycle()
  val localExercises by remember(selectedMuscle) {
    exerciseModel.exercisesByMuscle(selectedMuscle)
  }.collectAsStateWithLifecycle(initialValue = emptyList())
  val accessibleWorkouts by exerciseModel.accessibleWorkouts
    .collectAsStateWithLifecycle(initialValue = emptyList())
  // The plan list itself (accessibleWorkouts) only updates when this same paging refresh
  // runs - reusing it rather than a separate sync path keeps this screen's "refresh the
  // plan list" in lockstep with the Calendar screen's own plan list.
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
    modifier = modifier,
    edgeToEdge = edgeToEdge,
    title = title,
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
      onPicked(exercise.name)
    },
    onClose = onClose,
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
