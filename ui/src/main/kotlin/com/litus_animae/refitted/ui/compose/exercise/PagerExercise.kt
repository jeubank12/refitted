@file:OptIn(FlowPreview::class)

package com.litus_animae.refitted.ui.compose.exercise

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import arrow.core.nonEmptyListOf
import com.litus_animae.refitted.data.device.WatchState
import com.litus_animae.refitted.identity.ConfigProvider
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.LocalFeatures
import com.litus_animae.refitted.ui.compose.exercise.set.ExerciseSetView
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.state.SetHistory
import com.litus_animae.refitted.ui.compose.state.Weight
import com.litus_animae.refitted.ui.compose.state.recordsByExerciseId
import com.litus_animae.refitted.ui.compose.util.RefittedTheme
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@FlowPreview
@Composable
fun PagerExerciseView(
  model: ExerciseViewModel = viewModel(),
  workoutPlan: WorkoutPlan?,
  contentPadding: PaddingValues,
  /** Mirrors ExerciseMainPane's Scaffold, which stops reserving the bottom nav-bar inset under
   * this same condition - passed down rather than recomputed so every consumer of the reclaimed
   * space agrees with the Scaffold's own decision in the same composition pass. */
  reclaimBottomInset: Boolean,
  /** Mirrors ExerciseMainPane's landscape+compactHeight top bar, which only spans the
   * instructions pane's width - this clears the instructions pane's content of that overlay
   * without pushing the controls pane (which gets no such bar) down too. */
  firstPaneTopPadding: Dp = 0.dp,
  setHistoryList: (SetHistory) -> Unit,
  /** `collapsed` is decided by the bar itself, which knows how much room the title needs. */
  setContextMenu: (@Composable RowScope.(collapsed: Boolean) -> Unit) -> Unit,
  onAlternateChange: (Int) -> Unit,
  onStartEditWeight: (Weight) -> Unit,
  onSetSaved: () -> Unit = {},
  onOpenHistory: () -> Unit = {},
  editing: Boolean = false,
  onAddExercise: () -> Unit = {},
  onAddAlternate: (ExerciseSet) -> Unit = {},
  scrollToExerciseName: String? = null
) {
  val allRecords by model.records.collectAsState(initial = emptyList())
  val setRecords = recordsByExerciseId(allRecords = allRecords)

  val instructions by model.exercises.collectAsState(initial = emptyList(), Dispatchers.IO)
  val pagerState = rememberPagerState(pageCount = { instructions.size })

  // Land on an exercise just added from the add-exercise flow instead of wherever the pager
  // otherwise starts - fires once the newly-inserted row has actually loaded, then clears
  // itself so later unrelated recompositions of `instructions` don't re-trigger the scroll.
  var pendingScrollTarget by remember(scrollToExerciseName) { mutableStateOf(scrollToExerciseName) }
  LaunchedEffect(instructions, pendingScrollTarget) {
    val target = pendingScrollTarget ?: return@LaunchedEffect
    val targetIndex = instructions.indexOfFirst { instruction ->
      instruction.sets.any { it.exerciseName == target }
    }
    if (targetIndex >= 0) {
      // A just-added alternate joins an existing card rather than making its own, so landing on
      // the card isn't enough - surface the alternate itself. indexOfLast picks the newest, since
      // alternates sort after their base.
      val targetInstruction = instructions[targetIndex]
      val alternateIndex = targetInstruction.sets.indexOfLast { it.exerciseName == target }
      if (targetInstruction.hasAlternate && alternateIndex >= 0) {
        targetInstruction.activateAlternate(alternateIndex)
      }
      pagerState.scrollToPage(targetIndex)
      pendingScrollTarget = null
    }
  }
  // While a finger is down the index holds at the settled page — releasing is what
  // commits the change. Once released (fling included), targetPage knows the
  // destination immediately, so the bottom half doesn't wait out the coast animation.
  val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
  val displayedPage by remember(pagerState) {
    derivedStateOf { if (isDragged) pagerState.settledPage else pagerState.targetPage }
  }
  val instruction by remember(pagerState) { derivedStateOf { instructions.getOrNull(displayedPage) } }
  val exerciseSetFlow = remember(instruction, workoutPlan?.globalAlternate) {
    instruction?.set(workoutPlan?.globalAlternate)
  }
  val exerciseSet by exerciseSetFlow
    ?.collectAsState(initial = null, Dispatchers.IO)
    ?: remember { mutableStateOf(null) }
  val isRefreshing by model.isLoading.collectAsStateWithLifecycle()
  val watchSessionActive by model.watchSessionActive.collectAsStateWithLifecycle()

  val currentSetRecord = exerciseSet?.let { setRecords[it.id] }

  LaunchedEffect(exerciseSet) {
    setContextMenu { collapsed ->
      // Rendered here rather than in the top bar itself because this is where the displayed
      // exercise is known - it lands next to the add-exercise icon either way.
      if (LocalFeatures.current.flags[ConfigProvider.Companion.Feature.WATCH_SYNC] == "enabled") {
        val watchState by model.watchState.collectAsStateWithLifecycle()
        var showWatchSync by remember { mutableStateOf(false) }
        // Always tappable - the old enabled/disabled gating hid *why* nothing happened when
        // there was no device or the watch app wasn't open. Now every tap opens the dialog,
        // which shows that status directly instead of just refusing the tap.
        IconButton({ showWatchSync = true }) {
          Icon(
            if (watchState is WatchState.Active) Icons.Default.Check else Icons.Default.Watch,
            tint = if (watchState is WatchState.Active) {
              MaterialTheme.colorScheme.secondary
            } else {
              LocalContentColor.current
            },
            // TODO localize
            contentDescription = if (watchState is WatchState.Active) {
              "watch session in progress"
            } else {
              "send plan to watch"
            }
          )
        }
        if (showWatchSync) {
          WatchSyncDialog(
            model = model,
            globalAlternate = workoutPlan?.globalAlternate,
            onDismissRequest = { showWatchSync = false }
          )
        }
      }
      if (editing && workoutPlan?.isCustom == true) {
        exerciseSet?.let { set ->
          if (collapsed) {
            var expanded by remember { mutableStateOf(false) }
            IconButton({ expanded = true }) {
              // TODO localize
              Icon(Icons.Default.MoreVert, "more actions")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              Text(
                stringResource(id = R.string.add_alternate_exercise),
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    expanded = false
                    onAddAlternate(set)
                  }
                  .padding(start = 5.dp, end = 15.dp)
                  .padding(vertical = 5.dp)
              )
            }
          } else {
            // Default textButtonColors resolve to colors.primary, which is the top bar's own
            // background - take the bar's content color instead.
            TextButton(
              { onAddAlternate(set) },
              colors = ButtonDefaults.textButtonColors(
                contentColor = LocalContentColor.current
              )
            ) {
              Text(stringResource(id = R.string.alternate))
            }
          }
        }
      }
    }
  }
  // Split from the effect above and keyed on allSets (not currentSetRecord, which rebuilds every
  // completed set): currentSetRecord can resolve after exerciseSet, and keying only on
  // exerciseSet let that race latch the previous exercise's SetHistory permanently.
  LaunchedEffect(currentSetRecord?.allSets) {
    currentSetRecord?.let { setHistoryList(SetHistory(it.allSets)) }
  }

  // A genuinely empty day (no instructions, e.g. a fresh custom day) never resolves an
  // exerciseSet/currentSetRecord - only treat those as "still loading" when there's an
  // instruction they should eventually resolve against.
  val showRefreshIndicator = isRefreshing ||
    (instructions.isNotEmpty() && (exerciseSet == null || currentSetRecord == null))

  PullToRefreshBox(
    isRefreshing = showRefreshIndicator,
    onRefresh = model::refreshExercises,
    // consumeWindowInsets so anything further down reading WindowInsets.navigationBars directly
    // (ExerciseSetView's rest timer growth, PagerDetailView's instructions-pane protection) sees
    // it already reserved here rather than double-counting it - see ExerciseMainPane's Scaffold.
    modifier = Modifier
      .padding(contentPadding)
      .consumeWindowInsets(contentPadding)
  ) {
    if (workoutPlan?.isCustom == true && instructions.isEmpty() && !isRefreshing) {
      EmptyCustomDay(onAddExercise = if (editing) onAddExercise else null)
    } else {
      Column {
        PagerDetailView(
          instructions = instructions,
          pagerState = pagerState,
          activeSetWithRecord = currentSetRecord,
          displayedPage = displayedPage,
          reclaimBottomInset = reclaimBottomInset,
          firstPaneTopPadding = firstPaneTopPadding,
          globalAlternate = workoutPlan?.globalAlternate,
          workoutPlan = workoutPlan,
          onAlternateChange = onAlternateChange,
          setRecords = setRecords,
          timerStateByExerciseId = model.timerStateByExerciseId,
          onTimerToggle = { id, running, restSecs -> model.setTimerRunning(id, running, restSecs) },
          editing = editing,
          onUpdateCustomTargets = { workout, day, step, sets, reps, rest, repsRange ->
            model.updateCustomExerciseSetTargets(workout, day, step, sets, reps, rest, repsRange)
          },
          onDeleteExercise = { workout, day, step -> model.deleteExercise(workout, day, step) },
          onEditNote = { workout, day, step, note ->
            model.updateCustomExerciseSetNote(workout, day, step, note)
          },
          onSave = { updatedRecord ->
            val savedRecord = updatedRecord.copy(stored = true)
            currentSetRecord!!.saveRecordInState(savedRecord)
            model.saveExercise(
              SetRecord(savedRecord.weight, savedRecord.reps, savedRecord.set)
            )
            onSetSaved()
            // Superset auto-advance
            instruction?.offsetToNextSuperSet?.let { offset ->
              val isChallengeSet = exerciseSet!!.sets < 0
              val isLastSet = currentSetRecord.numCompleted >= exerciseSet!!.sets - 1
              val isLastExerciseInSuperset = offset <= 0
              if (isChallengeSet || !isLastSet || !isLastExerciseInSuperset)
                pagerState.requestScrollToPage(pagerState.settledPage + offset)
            }
          },
          onStartEditWeight = onStartEditWeight,
          onOpenHistory = onOpenHistory,
          watchSessionActive = watchSessionActive
        )
      }
    }
  }
}

// A null onAddExercise means this day was opened outside edit mode - exercises can only be added
// from the edit-mode calendar, so the button (and its prompt) is left off entirely.
@Composable
private fun EmptyCustomDay(onAddExercise: (() -> Unit)?, modifier: Modifier = Modifier) {
  Column(
    modifier
      .fillMaxSize()
      .padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      Icons.Default.FitnessCenter,
      // TODO localize
      contentDescription = null,
      modifier = Modifier.size(56.dp),
      tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)
    )
    Spacer(Modifier.height(12.dp))
    // TODO localize
    Text("No exercises yet", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
      // TODO localize
      if (onAddExercise != null)
        "Build this day as you train. There are no set limits the first time — targets fill in from what you complete."
      else
        "Open this day from edit mode on the calendar to add exercises.",
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )
    if (onAddExercise != null) {
      Spacer(Modifier.height(16.dp))
      Button(onClick = onAddExercise) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        // TODO localize
        Text("Add exercise")
      }
    }
  }
}

@Composable
fun PagerDetailView(
  instructions: List<ExerciseViewModel.ExerciseInstruction>,
  pagerState: PagerState,
  activeSetWithRecord: ExerciseSetWithRecord?,
  /** The page the detail pane reflects — commits on release rather than tracking the drag. */
  displayedPage: Int = pagerState.settledPage,
  /** Mirrors ExerciseMainPane's Scaffold, which stops reserving the bottom nav-bar inset under
   * this same condition - passed down rather than recomputed so every consumer of the reclaimed
   * space agrees with the Scaffold's own decision in the same composition pass. */
  reclaimBottomInset: Boolean = false,
  /** Mirrors ExerciseMainPane's landscape+compactHeight top bar, which only spans the
   * instructions pane's width - clears its content of that overlay without affecting the
   * controls pane below, which gets no such bar. */
  firstPaneTopPadding: Dp = 0.dp,
  /** Plan-wide alternate override for instructions with shared global alternate labels. */
  globalAlternate: Int? = null,
  /** Source of `globalAlternate` and any plan-wide alternate name overrides for the card's chip. */
  workoutPlan: WorkoutPlan? = null,
  onAlternateChange: (Int) -> Unit = {},
  setRecords: Map<String, ExerciseSetWithRecord> = emptyMap(),
  timerStateByExerciseId: Map<String, ExerciseViewModel.TimerState> = emptyMap(),
  onTimerToggle: (id: String, running: Boolean, restSeconds: Int) -> Unit = { _, _, _ -> },
  onSave: (Record) -> Unit,
  onStartEditWeight: (Weight) -> Unit,
  onOpenHistory: () -> Unit = {},
  editing: Boolean = false,
  onUpdateCustomTargets: (
    workout: String, day: String, step: String, sets: Int, reps: Int, rest: Int, repsRange: Int
  ) -> Unit = { _, _, _, _, _, _, _ -> },
  onDeleteExercise: (workout: String, day: String, step: String) -> Unit = { _, _, _ -> },
  onEditNote: (workout: String, day: String, step: String, note: String) -> Unit = { _, _, _, _ -> },
  /** The watch owns rest display/countdown while a session is active - the phone suppresses its own. */
  watchSessionActive: Boolean = false,
) {
  val scope = rememberCoroutineScope()
  val exerciseSetId = activeSetWithRecord?.exerciseSet?.id

  // The ring is anchored to whichever timer is currently running — not necessarily the settled
  // pager page. Swiping moves the weight/reps controls but the countdown ring stays put.
  val activeRunningEntry = timerStateByExerciseId.entries.firstOrNull { it.value.isRunning }
  val activeRunningTimerState = activeRunningEntry?.value
  val anyTimerRunning = activeRunningTimerState != null

  // Ring shows the running timer's rest duration; +/- controls apply to the settled exercise
  val ringRestSeconds = when {
    activeRunningTimerState != null -> activeRunningTimerState.restSeconds
    exerciseSetId != null -> activeSetWithRecord.exerciseSet.rest
    else -> 0
  }

  // "Next" preview — only meaningful while resting, and only for an exercise that still
  // has sets left (no point previewing a rest for an exercise you won't do again today).
  // Same exercise as the one resting: preview is that timer's own duration (sticky —
  // the next set of the same exercise rests the same length). Different exercise (swiped
  // ahead while another rests): preview is the displayed exercise's own rest instead.
  val isViewingDifferentExerciseThanRunning =
    activeRunningEntry != null && activeRunningEntry.key != exerciseSetId
  val displayedExerciseHasRecordToday = (activeSetWithRecord?.numCompleted ?: 0) > 0
  val nextRestSeconds = when {
    activeSetWithRecord?.exerciseIncomplete == false -> null
    isViewingDifferentExerciseThanRunning ->
      exerciseSetId?.let { activeSetWithRecord.exerciseSet.rest }
    displayedExerciseHasRecordToday -> ringRestSeconds
    else -> null
  }

  AdaptiveExercisePanes(
    modifier = Modifier.fillMaxSize(),
    splitRatio = ExercisePanesSplitRatio,
    gap = ExercisePanesGap,
    first = {
      // A no-op when PagerExerciseView's PullToRefreshBox already consumed navigationBars
      // (portrait, or landscape at medium+ height) - only actually reserves space when
      // ExerciseMainPane's Scaffold left it unconsumed for ExerciseSetView's rest timer to grow
      // into instead (reclaimBottomInset), since this pane isn't exempted from it.
      Box(
        Modifier
          .padding(top = firstPaneTopPadding)
          .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
      ) {
        PagerExerciseInstructions(
          instructions = instructions,
          pagerState = pagerState,
          alternateIndex = globalAlternate,
          workoutPlan = workoutPlan,
          onAlternateChange = onAlternateChange,
          setRecords = setRecords,
          editing = editing,
          onDeleteExercise = { set -> onDeleteExercise(set.workout, set.day, set.step) },
          onEditNote = { set, note -> onEditNote(set.workout, set.day, set.step, note) }
        )
      }
    },
    second = {
      if (activeSetWithRecord == null) {
        Box(Modifier.fillMaxSize())
      } else {
        val orientation = LocalConfiguration.current.orientation
        ExerciseSetView(
          modifier = Modifier
            .fillMaxSize()
            .padding(top= if(orientation == Configuration.ORIENTATION_LANDSCAPE) 16.dp else 0.dp, start=16.dp, end = 16.dp, bottom = 16.dp),
          reclaimBottomInset = reclaimBottomInset,
          setWithRecord = activeSetWithRecord,
          currentIndex = displayedPage,
          maxIndex = instructions.size - 1,
          updateIndex = { newIndex, record ->
            activeSetWithRecord.saveRecordInState(record)
            scope.launch { pagerState.animateScrollToPage(newIndex) }
          },
          onSave = onSave,
          onStartEditWeight = onStartEditWeight,
          showNavigationButtons = false,
          // Always pass the active running timer (it may be from a different exercise)
          externalTimerState = activeRunningTimerState,
          onTimerToggle = exerciseSetId?.let {
            {
              if (anyTimerRunning) {
                // Stop whichever timer is running (could be any exercise)
                onTimerToggle(activeRunningEntry.key, false, 0)
              } else {
                // Start a timer for the settled exercise
                onTimerToggle(it, true, activeSetWithRecord.exerciseSet.rest)
              }
            }
          },
          restOverride = ringRestSeconds,
          watchSessionActive = watchSessionActive,
          // Rest is freely adjustable in edit mode only - unconditionally, not gated on
          // completion state - and writes straight through to the persisted set, same path
          // as the sets/reps target editor. Outside edit mode the prescribed rest is fixed.
          onRestOverrideChange = if (editing && exerciseSetId != null) {
            { secs: Int ->
              onUpdateCustomTargets(
                activeSetWithRecord.exerciseSet.workout,
                activeSetWithRecord.exerciseSet.day,
                activeSetWithRecord.exerciseSet.step,
                activeSetWithRecord.exerciseSet.sets,
                activeSetWithRecord.exerciseSet.reps,
                secs,
                activeSetWithRecord.exerciseSet.repsRange
              )
            }
          } else null,
          nextRestSeconds = nextRestSeconds,
          editing = editing,
          onUpdateCustomTargets = { sets, reps, repsRange ->
            onUpdateCustomTargets(
              activeSetWithRecord.exerciseSet.workout,
              activeSetWithRecord.exerciseSet.day,
              activeSetWithRecord.exerciseSet.step,
              sets,
              reps,
              activeSetWithRecord.exerciseSet.rest,
              repsRange
            )
          },
          onOpenHistory = onOpenHistory
        )
      }
    }
  )
}

@Preview(showBackground = true, apiLevel = 36)
@Preview(showBackground = true, device = "spec:parent=pixel_5,orientation=landscape", apiLevel = 36)
@Composable
private fun PreviewPagerDetailView(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  RefittedTheme(darkTheme = true) {
    val records = remember { mutableStateListOf<Record>() }
    val currentRecord =
      remember { mutableStateOf(Record(25.0, exerciseSet.reps(0), exerciseSet, Instant.now())) }
    val pagerState = rememberPagerState { 3 }
    Column {
      PagerDetailView(
        instructions = IntArray(3) { 1 }.asList().map { _ ->
          ExerciseViewModel.ExerciseInstruction(
            nonEmptyListOf(exerciseSet),
            null,
            MutableStateFlow(0),
            ExerciseViewModel.AlternateSelection()
          )
        },
        pagerState = pagerState,
        activeSetWithRecord = ExerciseSetWithRecord(
          exerciseSet,
          currentRecord,
          numCompleted = 1,
          setRecords = records,
          allSets = emptyFlow()
        ),
        onSave = { },
        onStartEditWeight = {}
      )
    }
  }
}
