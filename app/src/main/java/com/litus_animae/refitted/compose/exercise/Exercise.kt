@file:OptIn(FlowPreview::class)

package com.litus_animae.refitted.compose.exercise

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.window.layout.DisplayFeature
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import com.google.accompanist.adaptive.VerticalTwoPaneStrategy
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.compose.state.Weight
import com.litus_animae.refitted.compose.state.recordsByExerciseId
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

@OptIn(ExperimentalMaterialApi::class)
@FlowPreview
@Composable
fun ExerciseView(
  model: ExerciseViewModel = viewModel(),
  workoutPlan: WorkoutPlan?,
  contentPadding: PaddingValues,
  setHistoryList: (Flow<PagingData<SetRecord>>) -> Unit,
  setContextMenu: (@Composable RowScope.() -> Unit) -> Unit,
  onAlternateChange: (Int) -> Unit,
  onStartEditWeight: (Weight) -> Unit
) {
  // TODO why is this re-evaluated every time?
  val allRecords by model.records.collectAsState(initial = emptyList())
  val setRecords = recordsByExerciseId(allRecords = allRecords)

  // TODO not saving, perhaps need rememberSaveableStateHolder
  val (index, setIndex) = rememberSaveable { mutableIntStateOf(0) }
  val instructions by model.exercises.collectAsState(initial = emptyList(), Dispatchers.IO)
  val instruction by remember(index) { derivedStateOf { instructions.getOrNull(index) } }
  val exerciseSet by instruction?.set(workoutPlan?.globalAlternate)
    ?.collectAsState(initial = null, Dispatchers.IO)
    ?: remember { mutableStateOf<ExerciseSet?>(null) }
  val isRefreshing by model.isLoading.collectAsState()

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

  // FIXME the column doesn't fill the full space and pull to refresh only works on content
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
      DetailView(
        index,
        instructions.size - 1,
        setWithRecord = currentSetRecord,
        updateIndex = { newIndex, updatedRecord ->
          currentSetRecord!!.saveRecordInState(updatedRecord)
          setIndex(newIndex)
        },
        onSave = { updatedRecord ->
          val savedRecord = updatedRecord.copy(stored = true)
          currentSetRecord!!.saveRecordInState(savedRecord)
          model.saveExercise(
            SetRecord(
              savedRecord.weight,
              savedRecord.reps,
              savedRecord.set
            )
          )
          instruction?.offsetToNextSuperSet?.map {
            // TODO if previous sets are incomplete, then nav to them
            // TODO if all challenge sets are complete, don't nav
            val isChallengeSet = exerciseSet!!.sets < 0
            val isLastSet = currentSetRecord.numCompleted >= exerciseSet!!.sets - 1
            val isLastExerciseInSuperset = it <= 0
            if (isChallengeSet || !isLastSet || !isLastExerciseInSuperset)
              setIndex(index + it)
          }
        },
        onStartEditWeight = onStartEditWeight
      )
    }
  }
}

@Preview(showBackground = true)
@Preview(showBackground = true, device = "spec:parent=pixel_5,orientation=landscape")
@Composable
fun PreviewDetailView(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  MaterialTheme(Theme.darkColors) {
    val records = remember { mutableStateListOf<Record>() }
    val currentRecord =
      remember { mutableStateOf(Record(25.0, exerciseSet.reps(0), exerciseSet, Instant.now())) }
    Column {
      DetailView(
        index = 0,
        maxIndex = 2,
        setWithRecord = ExerciseSetWithRecord(
          exerciseSet,
          currentRecord,
          numCompleted = 1,
          setRecords = records,
          allSets = emptyFlow()
        ),
        updateIndex = { _, _ -> },
        onSave = { },
        onStartEditWeight = {})
    }
  }
}

@Composable
fun DetailView(
  index: Int,
  maxIndex: Int,
  setWithRecord: ExerciseSetWithRecord?,
  updateIndex: (Int, Record) -> Unit,
  onSave: (Record) -> Unit,
  onStartEditWeight: (Weight) -> Unit
) {
  // TODO support fold by specifying features
  val displayFeatures = emptyList<DisplayFeature>()
  val (strategy, paddingValuesFirst, paddingValuesSecond) =
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE)
      Triple(
        HorizontalTwoPaneStrategy(0.5f, 16.dp),
        PaddingValues(top = 16.dp, start = 16.dp, bottom = 16.dp),
        PaddingValues(top = 16.dp, end = 16.dp, bottom = 16.dp)
      )
    else Triple(
      VerticalTwoPaneStrategy(0.5f, 16.dp),
      PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp),
      PaddingValues(start = 16.dp, bottom = 16.dp, end = 16.dp)
    )
  TwoPane(
    @Composable { ExerciseInstructions(setWithRecord, Modifier.padding(paddingValuesFirst)) },
    @Composable {
      // FIXME not pretty without the cards there, should pass this null check in deeper for navigation/buttons only
      if (setWithRecord != null)
        ExerciseSetView(
          setWithRecord,
          index,
          maxIndex,
          updateIndex,
          onSave,
          onStartEditWeight,
          Modifier.padding(paddingValuesSecond)
        )
    },
    strategy = strategy,
    displayFeatures = displayFeatures
  )
}