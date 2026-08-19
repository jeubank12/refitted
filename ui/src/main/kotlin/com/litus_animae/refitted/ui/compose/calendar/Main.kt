package com.litus_animae.refitted.ui.compose.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.compose.Changelog
import com.litus_animae.refitted.ui.models.UserViewModel
import com.litus_animae.refitted.ui.models.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun Calendar(
  modifier: Modifier = Modifier,
  navigateToWorkoutDay: (WorkoutPlan, Int, editing: Boolean) -> Unit,
  workoutModel: WorkoutViewModel = viewModel(),
  userModel: UserViewModel = viewModel()
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var showCreateCustomDialog by rememberSaveable { mutableStateOf(false) }
  var showCopyDayDialog by rememberSaveable { mutableStateOf(false) }
  var editMode by rememberSaveable { mutableStateOf(false) }
  // True once the user explicitly opens the plan menu at Compact width - the calendar is always
  // the default/home screen, this is only ever set by an explicit tap, never derived from
  // selection state, so there's no state where the menu accidentally becomes the landing screen.
  var planMenuOpen by rememberSaveable { mutableStateOf(false) }
  // Rename/delete dialog targets, shared between the calendar overflow menu and the plan-list
  // row's long-press/swipe actions - transient dialog state, not worth surviving process death.
  var renameTarget by remember { mutableStateOf<String?>(null) }
  var renameError by remember { mutableStateOf<String?>(null) }
  var deleteTarget by remember { mutableStateOf<String?>(null) }

  val selectedWorkoutPlan by workoutModel.currentWorkout.collectAsState(
    initial = workoutModel.savedStateLastWorkoutPlan,
    Dispatchers.IO
  )
  // A freshly created custom plan has nothing to "use" yet - land straight in edit mode.
  LaunchedEffect(selectedWorkoutPlan?.workout) {
    if (selectedWorkoutPlan?.isCustom == true && selectedWorkoutPlan?.totalDays == 0) {
      editMode = true
    }
  }
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

  // Zero the built-in gutter - the two panes' own TopAppBars (matched in height/color) plus a
  // single hairline drawn at their shared boundary read as one continuous bar, which a visible
  // gutter would break apart into two separate-looking blocks.
  val windowAdaptiveInfo = currentWindowAdaptiveInfo()
  val directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(windowAdaptiveInfo)
    .let { it.copy(horizontalPartitionSpacerSize = 0.dp) }
  val focusedRole = if (planMenuOpen) ListDetailPaneScaffoldRole.List else ListDetailPaneScaffoldRole.Detail
  val scaffoldValue = calculateThreePaneScaffoldValue(
    maxHorizontalPartitions = directive.maxHorizontalPartitions,
    adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
    currentDestination = ThreePaneScaffoldDestinationItem<Nothing>(pane = focusedRole)
  )
  // Backed by MutableThreePaneScaffoldState + animateTo rather than passing scaffoldValue
  // straight through: the value-only overload snaps instantly, which is what made the menu
  // icon/title/pane content all jump independently instead of a single slide transition.
  val paneScaffoldState = remember { MutableThreePaneScaffoldState(scaffoldValue) }
  LaunchedEffect(scaffoldValue) { paneScaffoldState.animateTo(scaffoldValue) }

  // Whether the open/close icon should show at all is a question of window width - can both
  // panes fit together? - not of which pane is currently focused, so it stays constant across
  // every tap at a given width.
  val bothPanesFit = directive.maxHorizontalPartitions >= 2
  // bothPanesFit is width-only - an unfolded phone/tablet in portrait is comfortably Medium+
  // width but has plenty of height, so the compact-height squeeze (calendar's sidebar, the
  // shrunk single-line TopAppBars, the compact sign-in row with no "signed in as" caption) is
  // only warranted in landscape, where height is actually scarce. Same threshold as the
  // exercise screen's bothPanesFit height gate.
  val compactPaneLayout = bothPanesFit &&
    !windowAdaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(480)

  BackHandler(enabled = scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden) {
    planMenuOpen = false
  }

  Box(
    modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    ListDetailPaneScaffold(
      directive = directive,
      scaffoldState = paneScaffoldState,
      detailPane = {
        AnimatedPane {
          WorkoutDetailPane(
            selectedWorkoutPlan = selectedWorkoutPlan,
            completedDays = completedDays,
            savedSelectedPlanLoading = savedSelectedPlanLoading,
            completedDaysLoading = completedDaysLoading,
            editMode = editMode,
            onEditModeChange = { editMode = it },
            onRenameRequest = {
              renameTarget = it
              renameError = null
            },
            onDeleteRequest = { deleteTarget = it },
            onCopyDayRequest = { showCopyDayDialog = true },
            showMenuButton = !bothPanesFit,
            wideLayout = compactPaneLayout,
            onOpenMenu = { planMenuOpen = true },
            workoutModel = workoutModel,
            userModel = userModel,
            navigateToWorkoutDay = navigateToWorkoutDay
          )
        }
      },
      listPane = {
        AnimatedPane {
          WorkoutPlanListPane(
            workoutModel = workoutModel,
            userModel = userModel,
            snackbarHostState = snackbarHostState,
            showBackButton = !bothPanesFit,
            wideLayout = compactPaneLayout,
            selectedWorkoutName = selectedWorkoutPlan?.workout,
            onBack = { planMenuOpen = false },
            onSelect = {
              planMenuOpen = false
              if (it.workout != selectedWorkoutPlan?.workout) {
                editMode = false
                workoutModel.loadWorkoutDaysCompleted(it)
              }
            },
            onCreateCustom = {
              planMenuOpen = false
              showCreateCustomDialog = true
            },
            onRenameRequest = {
              renameTarget = it.workout
              renameError = null
            }
          )
        }
      }
    )
    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
  }

  if (showCreateCustomDialog) {
    NewCustomWorkoutDialog(
      onDismissRequest = { showCreateCustomDialog = false },
      onCreate = {
        workoutModel.createCustomWorkout(it)
        showCreateCustomDialog = false
      }
    )
  }

  if (showCopyDayDialog && selectedWorkoutPlan != null) {
    CopyDayDialog(
      totalDays = selectedWorkoutPlan!!.totalDays,
      onDismissRequest = { showCopyDayDialog = false },
      onCopy = { fromDay ->
        workoutModel.copyDay(selectedWorkoutPlan!!, fromDay)
        showCopyDayDialog = false
      }
    )
  }

  renameTarget?.let { target ->
    RenamePlanDialog(
      currentName = target,
      errorMessage = renameError,
      onDismissRequest = {
        renameTarget = null
        renameError = null
      },
      onRename = { newName ->
        renameError = null
        workoutModel.renameCustomWorkout(
          target,
          newName,
          onSuccess = { renameTarget = null },
          onError = { message -> renameError = message }
        )
      },
      onDelete = {
        renameTarget = null
        renameError = null
        deleteTarget = target
      }
    )
  }

  deleteTarget?.let { target ->
    DeletePlanConfirmDialog(
      planName = target,
      onDismissRequest = { deleteTarget = null },
      onConfirm = {
        workoutModel.deleteCustomWorkout(target)
        if (selectedWorkoutPlan?.workout == target) {
          editMode = false
        }
        deleteTarget = null
      }
    )
  }
}
