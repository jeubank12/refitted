@file:OptIn(FlowPreview::class)

package com.litus_animae.refitted.compose.exercise

import android.content.res.Configuration
import android.util.Log
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.window.layout.DisplayFeature
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import com.google.accompanist.adaptive.VerticalTwoPaneStrategy
import com.litus_animae.refitted.compose.exercise.set.ExerciseSetView
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.compose.state.Weight
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.Record
import com.litus_animae.refitted.models.WorkoutPlan
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
  setHistoryList: (Flow<PagingData<com.litus_animae.refitted.models.SetRecord>>) -> Unit,
  setContextMenu: (@Composable RowScope.() -> Unit) -> Unit,
  onAlternateChange: (Int) -> Unit,
  onStartEditWeight: (Weight) -> Unit
) {
  val screenState by model.screenState.collectAsStateWithLifecycle()

  // TODO not saving, perhaps need rememberSaveableStateHolder
  val (index, setIndex) = rememberSaveable { mutableIntStateOf(0) }
  val instructions = screenState.instructions
  val instruction by remember(index) { derivedStateOf { instructions.getOrNull(index) } }

  val activeIndex = instruction?.let {
    screenState.activeAlternateIndices[it.sets.head.primaryStep]
  } ?: 0
  val exerciseSet = instruction?.sets?.getOrNull(workoutPlan?.globalAlternate ?: activeIndex)

  val currentSetRecord = exerciseSet?.let { screenState.setRecords[it.id] }

  LaunchedEffect(currentSetRecord) {
    if (currentSetRecord != null) {
      model.selectSetToEdit(currentSetRecord)
    }
  }

  LaunchedEffect(exerciseSet, activeIndex) {
    setContextMenu {
      instruction?.let {
        ExerciseContextMenu(
          it,
          workoutPlan,
          onAlternateChange,
          activeIndex
        )
      }
    }
    currentSetRecord?.allSets?.let { setHistoryList(it) }
  }

  val showRefreshIndicator =
    screenState.isLoading || exerciseSet == null || currentSetRecord == null

  LaunchedEffect(screenState.isLoading, exerciseSet, currentSetRecord) {
    Log.d(
      "ExerciseView",
      "StateCheck: isLoading=${screenState.isLoading}, exerciseSet is null=${exerciseSet == null}, currentSetRecord is null=${currentSetRecord == null}, showRefreshIndicator=$showRefreshIndicator"
    )
  }

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
        editedRecord = screenState.editedRecord,
        updateIndex = { newIndex, _ ->
          // This is now handled in the ViewModel
          setIndex(newIndex)
        },
        onSave = {
          val savedRecord = model.saveCurrentRecord()
          if (savedRecord != null) {
            instruction?.offsetToNextSuperSet?.let {
              // TODO if previous sets are incomplete, then nav to them
              // TODO if all challenge sets are complete, don't nav
              val isChallengeSet = savedRecord.set.sets < 0
              val isLastSet = (currentSetRecord?.numCompleted ?: 0) >= savedRecord.set.sets - 1
              val isLastExerciseInSuperset = it <= 0
              if (isChallengeSet || !isLastSet || !isLastExerciseInSuperset)
                setIndex(index + it)
            }
          }
        },
        onStartEditWeight = onStartEditWeight,
        onRepsChange = model::onRepsChange,
        onWeightChange = model::onWeightChange
      )
    }
  }
}

@Preview(showBackground = true)
@Preview(showBackground = true, device = "spec:parent=pixel_5,orientation=landscape")
@Composable
fun PreviewDetailView(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  MaterialTheme(Theme.darkColors) {
    val record = Record(25.0, exerciseSet.reps(0), exerciseSet, Instant.now())
    Column {
      DetailView(
        index = 0,
        maxIndex = 2,
        setWithRecord = ExerciseSetWithRecord(
          exerciseSet,
          record,
          todaysRecords = emptyList(),
          allSets = emptyFlow()
        ),
        editedRecord = record,
        updateIndex = { _, _ -> },
        onSave = { },
        onStartEditWeight = {},
        onRepsChange = {},
        onWeightChange = {}
      )
    }
  }
}

@Composable
fun DetailView(
  index: Int,
  maxIndex: Int,
  setWithRecord: ExerciseSetWithRecord?,
  editedRecord: Record?,
  updateIndex: (Int, Record) -> Unit,
  onSave: (Record) -> Unit,
  onStartEditWeight: (Weight) -> Unit,
  onRepsChange: (Int) -> Unit,
  onWeightChange: (Double) -> Unit
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
    { ExerciseInstructions(setWithRecord, Modifier.padding(paddingValuesFirst)) },
    {
      // FIXME not pretty without the cards there, should pass this null check in deeper for navigation/buttons only
      if (setWithRecord != null && editedRecord != null) {
        ExerciseSetView(
          setWithRecord = setWithRecord,
          editedRecord = editedRecord,
          pageIndex = index,
          maxPageIndex = maxIndex,
          onSave = onSave,
          onStartEditWeight = onStartEditWeight,
          onRepsChange = onRepsChange,
          onWeightChange = onWeightChange
        )
      }
    },
    strategy = strategy,
    displayFeatures = displayFeatures
  )
}
