package com.litus_animae.refitted.ui.compose.exercise

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.exercise.input.WeightButtons
import com.litus_animae.refitted.ui.compose.state.SetHistory
import com.litus_animae.refitted.ui.compose.state.Weight
import com.litus_animae.refitted.ui.compose.util.appBarColors
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview

/**
 * The Main side of the Exercise screen's Main/Supporting split: the exercise pager itself, plus
 * the weight-edit sheet (a docked bottom sheet at every width, not part of the pane split).
 * Always the default screen; [SetRecordList] only appears once explicitly opened via the
 * History icon, or permanently alongside this pane at Medium+.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class, FlowPreview::class)
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
  /** Whether a display cutout's actual bounds overlap this pane - see exercise/Main.kt, which
   * measures both panes' real bounds against `rememberDisplayCutoutBoundingRects()` rather than
   * assuming a cutout affects whichever pane owns a given screen edge. */
  affectedByCutout: Boolean = true,
) {
  var showWeightSheet by remember { mutableStateOf(false) }
  val weightSheetState = rememberModalBottomSheetState()
  var sheetWeight by remember { mutableStateOf(Weight(0.0)) }
  var contextMenu by remember { mutableStateOf<@Composable RowScope.(Boolean) -> Unit>({}) }
  // Width-compact portrait phones have plenty of height and should keep the default bar - only
  // shrink it when height is actually the scarce dimension, independent of showHistoryButton/
  // bothPanesFit (which are width-only, see exercise/Main.kt).
  val compactHeight = !currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(480)
  val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val splitTopBar = isLandscape && compactHeight

  val title = stringResource(id = R.string.app_name)
  val dayWord = stringResource(id = R.string.day)
  val barTitle = "$title: $workoutId $dayWord $day"
  val showAddExercise = loadedWorkoutPlan?.isCustom == true && editing

  Scaffold(
    // navigationBars alone leaves a side-mounted camera cutout unhandled once rotated to
    // landscape — the two-pane exercise layout then splits content right up against it. Dropped
    // here when landscape+compact height so ExerciseSetView's rest timer can grow into it instead.
    // The cutout term itself is gated by affectedByCutout - a cutout whose bounds don't actually
    // overlap this pane (e.g. it's over SetRecordList's pane instead) shouldn't cost this pane
    // any width/height for it.
    contentWindowInsets = if (splitTopBar) {
      if (affectedByCutout) WindowInsets.displayCutout else WindowInsets(0, 0, 0, 0)
    } else {
      WindowInsets.navigationBars.union(
        if (affectedByCutout) WindowInsets.displayCutout else WindowInsets(0, 0, 0, 0)
      )
    },
    topBar = {
      if (splitTopBar) {
        // Just the status-bar sliver, full width - the actual title/nav/actions bar below only
        // spans the instructions pane's width as a content overlay, so Scaffold's contentPadding
        // (shared by both panes) reserves only the unavoidable status bar here, not the bar's own
        // height too. Keeps the top edge reading as one continuous band despite the bar itself
        // only extending over the instructions pane.
        Box(
          Modifier
            .fillMaxWidth()
            .background(appBarColors().containerColor)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
        )
      } else {
        ExerciseAppBar(
          barTitle = barTitle,
          showAddExercise = showAddExercise,
          expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight,
          windowInsets = TopAppBarDefaults.windowInsets.union(
            if (affectedByCutout) {
              WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
            } else {
              WindowInsets(0, 0, 0, 0)
            }
          ),
          contextMenu = contextMenu,
          showHistoryButton = showHistoryButton,
          onOpenHistory = onOpenHistory,
          onAddExercise = onAddExercise,
        )
      }
    }
  ) { contentPadding ->
    val layoutDirection = LocalLayoutDirection.current

    // Unpadded - PagerExerciseView below still consumes contentPadding itself exactly as the
    // non-split path always did, so a side-mounted camera cutout keeps pushing its actual
    // *content* (cards, text) clear of the notch. This wrapper and the single PagerExerciseView
    // call stay unconditional (only argument *values* branch on splitTopBar) so toggling
    // splitTopBar - e.g. a rotation or an unfold crossing the 480dp breakpoint - never remounts
    // PagerExerciseView's subtree, which would otherwise drop ExerciseSetView's in-progress
    // rememberSaveable weight/reps state.
    BoxWithConstraints(Modifier.fillMaxSize()) {
      PagerExerciseView(exerciseModel,
        workoutPlan = loadedWorkoutPlan,
        contentPadding = contentPadding,
        reclaimBottomInset = splitTopBar,
        firstPaneTopPadding = if (splitTopBar) ExerciseAppBarCompactHeight else 0.dp,
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

      if (splitTopBar) {
        // AdaptiveExercisePanes' instructions pane starts only after contentPadding's horizontal
        // component (the cutout) is consumed by PagerExerciseView above, so the bar's own *right*
        // edge must line up against that same post-consumption split - cutoutStart is added back
        // on top of it so the bar's *background* still reaches the true left edge instead of
        // stopping where the cutout's clearance begins (only this overlay's windowInsets below,
        // which insets its title/icon content, should respect the cutout).
        val cutoutStart = contentPadding.calculateStartPadding(layoutDirection)
        val cutoutEnd = contentPadding.calculateEndPadding(layoutDirection)
        val barWidth = cutoutStart +
          (maxWidth - cutoutStart - cutoutEnd - ExercisePanesGap) * ExercisePanesSplitRatio
        ExerciseAppBar(
          modifier = Modifier
            .align(Alignment.TopStart)
            // Scaffold draws its topBar slot (the status-bar band) above this content area in
            // z-order, both anchored at the same y=0 - without this offset this overlay would
            // render at y=0 too and end up entirely hidden underneath that band instead of
            // appearing right below it.
            .padding(top = contentPadding.calculateTopPadding())
            .width(barWidth),
          barTitle = barTitle,
          showAddExercise = showAddExercise,
          expandedHeight = ExerciseAppBarCompactHeight,
          // The status bar is already reserved by the band in Scaffold's topBar above - this
          // overlay sits below it in the content area, so it only needs the horizontal cutout
          // clearance (unconsumed here, unlike PagerExerciseView's own content), not another top
          // inset stacked on top.
          windowInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
          contextMenu = contextMenu,
          showHistoryButton = showHistoryButton,
          onOpenHistory = onOpenHistory,
          onAddExercise = onAddExercise,
        )
      }
    }

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

// The compactHeight bar height - shared by the landscape+compactHeight overlay and its
// Scaffold-slot counterpart so both agree on one value.
private val ExerciseAppBarCompactHeight = 48.dp

/**
 * The exercise screen's title/nav-icon/actions bar. Shared by both of ExerciseMainPane's uses:
 * the ordinary full-width Scaffold topBar, and (landscape+compactHeight only) a left-pane-only
 * overlay in the content area - only [expandedHeight] and [windowInsets] differ between the two,
 * everything else (title measurement/collapse logic, action placement) is identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseAppBar(
  barTitle: String,
  showAddExercise: Boolean,
  expandedHeight: Dp,
  windowInsets: WindowInsets,
  contextMenu: @Composable RowScope.(collapsed: Boolean) -> Unit,
  showHistoryButton: Boolean,
  onOpenHistory: () -> Unit,
  onAddExercise: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier) {
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
      title = { Text(barTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      expandedHeight = expandedHeight,
      windowInsets = windowInsets,
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
