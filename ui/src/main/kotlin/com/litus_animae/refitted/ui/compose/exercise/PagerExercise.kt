@file:OptIn(FlowPreview::class)

package com.litus_animae.refitted.ui.compose.exercise

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import arrow.core.nonEmptyListOf
import com.litus_animae.refitted.ui.compose.exercise.set.ExerciseSetView
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.state.Weight
import com.litus_animae.refitted.ui.compose.state.recordsByExerciseId
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.data.models.WorkoutPlan
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterialApi::class)
@FlowPreview
@Composable
fun PagerExerciseView(
  model: ExerciseViewModel = viewModel(),
  workoutPlan: WorkoutPlan?,
  contentPadding: PaddingValues,
  setHistoryList: (Flow<PagingData<SetRecord>>) -> Unit,
  setContextMenu: (@Composable RowScope.() -> Unit) -> Unit,
  onAlternateChange: (Int) -> Unit,
  onStartEditWeight: (Weight) -> Unit
) {
  val allRecords by model.records.collectAsState(initial = emptyList())
  val setRecords = recordsByExerciseId(allRecords = allRecords)

  val instructions by model.exercises.collectAsState(initial = emptyList(), Dispatchers.IO)
  val pagerState = rememberPagerState(pageCount = { instructions.size })
  val instruction by remember(pagerState) { derivedStateOf { instructions.getOrNull(pagerState.settledPage) } }
  val exerciseSet by instruction?.set(workoutPlan?.globalAlternate)
    ?.collectAsState(initial = null, Dispatchers.IO)
    ?: remember { mutableStateOf<ExerciseSet?>(null) }
  val isRefreshing by model.isLoading.collectAsStateWithLifecycle()
  val maxRestSeconds by model.maxRestSeconds.collectAsState(initial = 0)

  val currentSetRecord = exerciseSet?.let { setRecords[it.id] }

  LaunchedEffect(exerciseSet) {
    setContextMenu { instruction?.let { ExerciseContextMenu(it, workoutPlan, onAlternateChange) } }
    currentSetRecord?.allSets?.let { setHistoryList(it) }
  }

  val showRefreshIndicator = isRefreshing || exerciseSet == null || currentSetRecord == null
  val pullRefreshState =
    rememberPullRefreshState(
      refreshing = showRefreshIndicator,
      onRefresh = model::refreshExercises
    )

  Box(
    modifier = Modifier
      .pullRefresh(pullRefreshState)
      .padding(contentPadding)
  ) {
    PullRefreshIndicator(
      refreshing = showRefreshIndicator,
      state = pullRefreshState,
      Modifier
        .align(Alignment.TopCenter)
        .zIndex(100f)
    )
    Column {
      PagerDetailView(
        instructions = instructions,
        pagerState = pagerState,
        activeSetWithRecord = currentSetRecord,
        setRecords = setRecords,
        maxRestSeconds = maxRestSeconds,
        timerStateByExerciseId = model.timerStateByExerciseId,
        restOverrideByExerciseId = model.restOverrideByExerciseId,
        onTimerToggle = { id, running, restSecs -> model.setTimerRunning(id, running, restSecs) },
        onRestOverrideChange = { id, secs -> model.setRestOverride(id, secs) },
        onSave = { updatedRecord ->
          val savedRecord = updatedRecord.copy(stored = true)
          currentSetRecord!!.saveRecordInState(savedRecord)
          model.saveExercise(
            SetRecord(savedRecord.weight, savedRecord.reps, savedRecord.set)
          )
          // Superset auto-advance
          instruction?.offsetToNextSuperSet?.let { offset ->
            val isChallengeSet = exerciseSet!!.sets < 0
            val isLastSet = currentSetRecord.numCompleted >= exerciseSet!!.sets - 1
            val isLastExerciseInSuperset = offset <= 0
            if (isChallengeSet || !isLastSet || !isLastExerciseInSuperset)
              pagerState.requestScrollToPage(pagerState.settledPage + offset)
          }
        },
        onStartEditWeight = onStartEditWeight
      )
    }
  }
}

@Composable
fun PagerDetailView(
  instructions: List<ExerciseViewModel.ExerciseInstruction>,
  pagerState: PagerState,
  activeSetWithRecord: ExerciseSetWithRecord?,
  setRecords: Map<String, ExerciseSetWithRecord> = emptyMap(),
  maxRestSeconds: Int = 0,
  timerStateByExerciseId: Map<String, ExerciseViewModel.TimerState> = emptyMap(),
  restOverrideByExerciseId: Map<String, Int> = emptyMap(),
  onTimerToggle: (id: String, running: Boolean, restSeconds: Int) -> Unit = { _, _, _ -> },
  onRestOverrideChange: (id: String, seconds: Int) -> Unit = { _, _ -> },
  onSave: (Record) -> Unit,
  onStartEditWeight: (Weight) -> Unit
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
    exerciseSetId != null -> restOverrideByExerciseId[exerciseSetId] ?: activeSetWithRecord?.exerciseSet?.rest ?: 0
    else -> 0
  }

  AdaptiveExercisePanes(
    modifier = Modifier.fillMaxSize(),
    splitRatio = 0.45f,
    gap = 8.dp,
    first = {
      PagerExerciseInstructions(
        instructions = instructions,
        pagerState = pagerState,
        alternateIndex = 0,
        setRecords = setRecords
      )
    },
    second = {
      if (activeSetWithRecord == null) {
        Box(Modifier.fillMaxSize())
      } else {
        ExerciseSetView(
          modifier = Modifier
            .fillMaxSize()
            .padding(start=16.dp, end = 16.dp, bottom = 16.dp),
          setWithRecord = activeSetWithRecord,
          currentIndex = pagerState.settledPage,
          maxIndex = instructions.size - 1,
          updateIndex = { newIndex, record ->
            activeSetWithRecord.saveRecordInState(record)
            scope.launch { pagerState.animateScrollToPage(newIndex) }
          },
          onSave = onSave,
          onStartEditWeight = onStartEditWeight,
          showNavigationButtons = false,
          // Always pass the active running timer (may be a different exercise)
          externalTimerState = activeRunningTimerState,
          onTimerToggle = exerciseSetId?.let {
            {
              if (anyTimerRunning) {
                // Stop whichever timer is running (could be any exercise)
                onTimerToggle(activeRunningEntry!!.key, false, 0)
              } else {
                // Start a timer for the settled exercise
                val restSecs = restOverrideByExerciseId[it] ?: activeSetWithRecord.exerciseSet.rest
                onTimerToggle(it, true, restSecs)
              }
            }
          },
          maxRestSeconds = maxRestSeconds.coerceAtLeast(activeSetWithRecord.exerciseSet.rest),
          restOverride = ringRestSeconds,
          onRestOverrideChange = null   // rest adjustment not exposed in pager path for now
        )
      }
    }
  )
}

@Preview(showBackground = true)
@Preview(showBackground = true, device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun PreviewPagerDetailView(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  MaterialTheme(Theme.darkColors) {
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
            MutableStateFlow(0)
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
        maxRestSeconds = 90,
        onSave = { },
        onStartEditWeight = {}
      )
    }
  }
}
