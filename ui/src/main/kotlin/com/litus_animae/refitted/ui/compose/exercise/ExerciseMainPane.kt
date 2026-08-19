package com.litus_animae.refitted.ui.compose.exercise

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.exercise.input.WeightButtons
import com.litus_animae.refitted.ui.compose.state.SetHistory
import com.litus_animae.refitted.ui.compose.state.Weight
import com.litus_animae.refitted.ui.compose.util.appBarColors
import com.litus_animae.refitted.ui.models.ExerciseViewModel

/**
 * The Main side of the Exercise screen's Main/Supporting split: the exercise pager itself, plus
 * the weight-edit sheet (a docked bottom sheet at every width, not part of the pane split).
 * Always the default screen; [SetRecordList] only appears once explicitly opened via the
 * History icon, or permanently alongside this pane at Medium+.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExerciseMainPane(
  day: String,
  workoutId: String,
  editing: Boolean,
  loadedWorkoutPlan: WorkoutPlan?,
  exerciseModel: ExerciseViewModel,
  setHistoryList: (SetHistory) -> Unit,
  onAlternateChange: (Int) -> Unit,
  onSetSaved: () -> Unit,
  showHistoryButton: Boolean,
  onOpenHistory: () -> Unit,
  onAddExercise: () -> Unit,
  onAddAlternate: (ExerciseSet) -> Unit,
  scrollToExerciseName: String?,
) {
  var showWeightSheet by remember { mutableStateOf(false) }
  val weightSheetState = rememberModalBottomSheetState()
  var sheetWeight by remember { mutableStateOf(Weight(0.0)) }
  var contextMenu by remember { mutableStateOf<@Composable RowScope.(Boolean) -> Unit>({}) }
  // Width-compact portrait phones have plenty of height and should keep the default bar - only
  // shrink it when height is actually the scarce dimension, independent of showHistoryButton/
  // bothPanesFit (which are width-only, see exercise/Main.kt).
  val compactHeight = !currentWindowAdaptiveInfo().windowSizeClass.isHeightAtLeastBreakpoint(480)

  Scaffold(
    // navigationBars alone leaves a side-mounted camera cutout unhandled once rotated to
    // landscape — the two-pane exercise layout then splits content right up against it.
    contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    topBar = {
      val title = stringResource(id = R.string.app_name)
      val dayWord = stringResource(id = R.string.day)
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
          // The default 64dp content height plus the status bar inset above it eats a real
          // chunk of a short landscape window - shrink to something closer to Material2's
          // actionBar height, the insets themselves are unaffected. Only when height is
          // actually short - a portrait phone is width-compact too but has height to spare.
          expandedHeight = if (compactHeight) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
          windowInsets = TopAppBarDefaults.windowInsets.union(
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
          ),
          colors = appBarColors(),
          actions = {
            // Expanded, the labelled action reads as a peer of + and sits ahead of it;
            // collapsed, it is an overflow menu and belongs last instead.
            if (!collapsed) contextMenu(false)
            if (showAddExercise) {
              IconButton(onAddExercise) {
                // TODO localize
                Icon(Icons.Default.Add, "add exercise")
              }
            }
            if (collapsed) contextMenu(true)
          },
          navigationIcon = {
            if (showHistoryButton) {
              IconButton(onOpenHistory) {
                Icon(
                  Icons.Default.History,
                  // TODO localize
                  "history"
                )
              }
            }
          }
        )
      }
    }
  ) { contentPadding ->
    PagerExerciseView(exerciseModel,
      workoutPlan = loadedWorkoutPlan,
      contentPadding = contentPadding,
      setHistoryList = setHistoryList,
      setContextMenu = { contextMenu = it },
      onAlternateChange = onAlternateChange,
      onStartEditWeight = {
        sheetWeight = it
        showWeightSheet = true
      },
      onSetSaved = onSetSaved,
      onOpenHistory = onOpenHistory,
      editing = editing,
      onAddExercise = onAddExercise,
      onAddAlternate = onAddAlternate,
      scrollToExerciseName = scrollToExerciseName)

    if (showWeightSheet) {
      ModalBottomSheet(
        onDismissRequest = { showWeightSheet = false },
        sheetState = weightSheetState
      ) {
        Box(Modifier.padding(top = 10.dp, bottom = 10.dp)) {
          WeightButtons(sheetWeight)
        }
      }
    }
  }
}
