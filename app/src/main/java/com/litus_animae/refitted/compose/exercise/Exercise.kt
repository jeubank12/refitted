@file:OptIn(FlowPreview::class)

package com.litus_animae.refitted.compose.exercise

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.compose.state.recordsByExerciseId
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.Record
import com.litus_animae.refitted.models.SetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

@FlowPreview
@Composable
fun ExerciseView(
  model: ExerciseViewModel = viewModel(),
  setHistoryList: (Flow<PagingData<SetRecord>>) -> Unit,
  setContextMenu: (@Composable RowScope.() -> Unit) -> Unit
) {
  // TODO why is this re-evaluated every time?
  val allRecords by model.records.collectAsState(initial = emptyList())
  val setRecords = recordsByExerciseId(allRecords = allRecords)

  // TODO not saving, perhaps need rememberSaveableStateHolder
  val (index, setIndex) = rememberSaveable { mutableStateOf(0) }
  val instructions by model.exercises.collectAsState(initial = emptyList(), Dispatchers.IO)
  val instruction by derivedStateOf { instructions.getOrNull(index) }
  val exerciseSet by instruction?.set?.collectAsState(initial = null, Dispatchers.IO)
    ?: remember { mutableStateOf<ExerciseSet?>(null) }
  val isRefreshing by model.isLoading.collectAsState()

  val currentSetRecord = exerciseSet?.let { setRecords[it.id] }

  LaunchedEffect(exerciseSet) {
    setContextMenu { instruction?.let { ExerciseContextMenu(it) } }
    currentSetRecord?.allSets?.let { setHistoryList(it) }
  }

  val swipeRefreshState =
    rememberSwipeRefreshState(isRefreshing || exerciseSet == null || currentSetRecord == null)
  SwipeRefresh(state = swipeRefreshState, onRefresh = model::refreshExercises) {
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
      }
    )
  }
}

@Preview(showBackground = true)
@Composable
fun PreviewDetailView(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  MaterialTheme(Theme.darkColors) {
    val records = remember { mutableStateListOf<Record>() }
    val currentRecord =
      remember { mutableStateOf(Record(25.0, exerciseSet.reps, exerciseSet, Instant.now())) }
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
        onSave = { })
    }
  }
}

@Composable
fun DetailView(
  index: Int,
  maxIndex: Int,
  setWithRecord: ExerciseSetWithRecord?,
  updateIndex: (Int, Record) -> Unit,
  onSave: (Record) -> Unit
) {
  when (LocalConfiguration.current.orientation) {
    Configuration.ORIENTATION_LANDSCAPE ->
      Row(Modifier.padding(16.dp)) {
        ExerciseInstructions(setWithRecord?.exerciseSet, Modifier.weight(1f))
        if (setWithRecord != null)
          ExerciseSetView(
            setWithRecord,
            index,
            maxIndex,
            updateIndex,
            onSave,
            Modifier.weight(1f)
          )
      }
    else ->
      Column(Modifier.padding(16.dp)) {
        Row(Modifier.weight(1f)) {
          ExerciseInstructions(setWithRecord?.exerciseSet)
        }
        Row(Modifier.weight(1f)) {
          if (setWithRecord != null)
            ExerciseSetView(
              setWithRecord,
              index,
              maxIndex,
              updateIndex,
              onSave
            )
        }
      }
  }
}