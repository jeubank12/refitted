package com.litus_animae.refitted.ui.compose.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.util.LoadingView
import com.litus_animae.refitted.ui.compose.util.appBarColors
import com.litus_animae.refitted.ui.models.UserViewModel
import com.litus_animae.refitted.ui.models.WorkoutViewModel
import java.time.Instant

/**
 * The Detail side of the Calendar screen's List-Detail split: the previously-selected plan's
 * calendar, editing tools, and per-plan menu. This is always the default screen at Compact
 * width - [WorkoutPlanListPane] only appears once explicitly opened via the menu icon, or
 * permanently alongside this pane at Medium+. Sits to the right of [WorkoutPlanListPane]
 * ([ListDetailPaneScaffoldDefaults]'s pane order is List, Detail, Extra).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailPane(
  selectedWorkoutPlan: WorkoutPlan?,
  completedDays: Map<Int, Instant>,
  savedSelectedPlanLoading: Boolean,
  completedDaysLoading: Boolean,
  editMode: Boolean,
  onEditModeChange: (Boolean) -> Unit,
  onRenameRequest: (String) -> Unit,
  onDeleteRequest: (String) -> Unit,
  onCopyDayRequest: () -> Unit,
  showMenuButton: Boolean,
  // True when shown alongside WorkoutPlanListPane AND height is constrained (landscape) - the
  // calendar's legend and "hide rest days" toggle move into a vertical sidebar instead of
  // stacking, since landscape has width to spare but not much height.
  wideLayout: Boolean,
  onOpenMenu: () -> Unit,
  workoutModel: WorkoutViewModel,
  userModel: UserViewModel,
  navigateToWorkoutDay: (WorkoutPlan, Int, editing: Boolean) -> Unit,
  /** Whether a display cutout's actual bounds overlap this pane - see calendar/Main.kt, which
   * measures both panes' real bounds against `rememberDisplayCutoutBoundingRects()` rather than
   * assuming a cutout affects whichever pane owns a given screen edge. */
  affectedByCutout: Boolean = true,
) {
  var showAddMenu by rememberSaveable { mutableStateOf(false) }

  Scaffold(
    // navigationBars alone leaves a side-mounted camera cutout unhandled once rotated to
    // landscape. But affectedByCutout (calendar/Main.kt, measured against this pane's actual
    // bounds) is false whenever the cutout is nowhere near this pane - e.g. when both panes show
    // side by side and it's over WorkoutPlanListPane instead - so consuming the full cutout here
    // would pad width this pane doesn't need to give up and squish its own content for nothing.
    contentWindowInsets = WindowInsets.navigationBars.union(
      if (affectedByCutout) WindowInsets.displayCutout else WindowInsets(0, 0, 0, 0)
    ),
    topBar = {
      TopAppBar(
        title = {
          val appName = stringResource(id = R.string.app_name)
          if (selectedWorkoutPlan != null) Text(selectedWorkoutPlan.workout)
          else Text(appName)
        },
        // Matches WorkoutPlanListPane's bar height in wideLayout, since the two panes sit
        // side by side and should read as one continuous bar.
        expandedHeight = if (wideLayout) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
        windowInsets = TopAppBarDefaults.windowInsets.union(
          if (affectedByCutout) {
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
          } else {
            WindowInsets(0, 0, 0, 0)
          }
        ),
        colors = appBarColors(),
        navigationIcon = {
          if (showMenuButton) {
            IconButton(onOpenMenu) {
              Icon(
                Icons.Default.Menu,
                // TODO localize
                "menu"
              )
            }
          }
        },
        actions = {
          if (selectedWorkoutPlan != null) {
            val (expanded, setExpanded) = rememberSaveable { mutableStateOf(false) }
            val (alerted, setAlerted) = rememberSaveable { mutableStateOf(false) }
            IconButton({ setExpanded(!expanded) }) {
              // TODO localize
              Icon(Icons.Default.MoreVert, "workout menu")
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { setExpanded(false) }) {
              if (selectedWorkoutPlan.isCustom && !editMode) {
                Text(
                  "Edit plan",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      onEditModeChange(true)
                      setExpanded(false)
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
              }
              if (selectedWorkoutPlan.isCustom) {
                // TODO localize
                Text(
                  "Rename plan",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      onRenameRequest(selectedWorkoutPlan.workout)
                      setExpanded(false)
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
                // TODO localize
                Text(
                  "Delete plan",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      onDeleteRequest(selectedWorkoutPlan.workout)
                      setExpanded(false)
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
              }
              Text(
                "Reset workout",
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    setAlerted(true)
                    setExpanded(false)
                  }
                  .padding(start = 5.dp, end = 15.dp)
                  .padding(vertical = 5.dp))
              val isAdmin by userModel.userIsAdmin.collectAsStateWithLifecycle(initialValue = false)
              if (isAdmin) {
                Text(
                  "Crash",
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      throw RuntimeException("Test Crash")
                    }
                    .padding(start = 5.dp, end = 15.dp)
                    .padding(vertical = 5.dp))
              }
            }
            if (alerted) {
              AlertDialog(
                onDismissRequest = { setAlerted(false) },
                // TODO localize
                title = { Text("Reset Workout Completion") },
                text = { Text("This will reset your completed days. Are you sure? (This does not remove records of your previous exercise sets") },
                confirmButton = {
                  Button(onClick = {
                    workoutModel.resetWorkoutCompletion(
                      selectedWorkoutPlan
                    )
                    setAlerted(false)
                  }) {
                    Text("Yes")
                  }
                },
                dismissButton = {
                  Button(onClick = { setAlerted(false) }) {
                    Text("No")
                  }
                })
            }
          }
        }
      )
    },
    floatingActionButton = {
      if (selectedWorkoutPlan?.isCustom == true && editMode) {
        // FAB + its menu need to share one layout node in this slot - as two loose siblings,
        // Scaffold measured the slot's width from both and threw the FAB's End position off,
        // rendering it on the left.
        Box {
          FloatingActionButton(onClick = { showAddMenu = true }) {
            // TODO localize
            Icon(Icons.Default.Add, "add to plan")
          }
          DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
            Text(
              // TODO localize
              "New day",
              Modifier
                .fillMaxWidth()
                .clickable {
                  workoutModel.addDay(selectedWorkoutPlan)
                  showAddMenu = false
                }
                .padding(start = 5.dp, end = 15.dp)
                .padding(vertical = 5.dp))
            if (selectedWorkoutPlan.totalDays > 0) {
              Text(
                // TODO localize
                "Copy from…",
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    showAddMenu = false
                    onCopyDayRequest()
                  }
                  .padding(start = 5.dp, end = 15.dp)
                  .padding(vertical = 5.dp))
            }
            Text(
              // TODO localize
              "Rest day",
              Modifier
                .fillMaxWidth()
                .clickable {
                  workoutModel.addRestDay(selectedWorkoutPlan)
                  showAddMenu = false
                }
                .padding(start = 5.dp, end = 15.dp)
                .padding(vertical = 5.dp))
          }
        }
      }
    },
    floatingActionButtonPosition = FabPosition.End
  ) { contentPadding ->
    if (savedSelectedPlanLoading || (selectedWorkoutPlan != null && completedDaysLoading)) {
      Surface(
        Modifier
          .fillMaxSize()
          .padding(contentPadding)
      ) {
        LoadingView()
      }
    } else if (selectedWorkoutPlan == null) {
      // TODO instruction page
      Row(
        Modifier
          .padding(contentPadding)
          .padding(start = 10.dp, top = 10.dp)
          .fillMaxWidth()
      ) {
        Text("Open the menu to pick a workout")
      }
    } else {
      WorkoutCalendar(
        selectedWorkoutPlan,
        completedDays,
        contentPadding = contentPadding,
        wideLayout = wideLayout,
        editMode = editMode,
        onExitEdit = { onEditModeChange(false) },
        onSaveStartDate = { workoutModel.setStartDate(selectedWorkoutPlan, it) },
        onClearDay = { day -> workoutModel.clearDay(selectedWorkoutPlan, day) },
        onSetDayRest = { day, isRest -> workoutModel.setDayRest(selectedWorkoutPlan, day, isRest) },
        onEditDay = { day ->
          navigateToWorkoutDay(selectedWorkoutPlan, day, true)
          workoutModel.setLastViewedDay(selectedWorkoutPlan, day)
        }
      ) {
        navigateToWorkoutDay(selectedWorkoutPlan, it, false)
        workoutModel.setLastViewedDay(selectedWorkoutPlan, it)
      }
    }
  }
}
