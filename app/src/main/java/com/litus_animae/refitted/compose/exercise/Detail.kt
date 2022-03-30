@file:OptIn(FlowPreview::class)

package com.litus_animae.refitted.compose.exercise

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.compose.state.Record
import com.litus_animae.refitted.compose.state.recordsByExerciseId
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.SetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@FlowPreview
@Composable
fun ExerciseDetails(
  model: ExerciseViewModel = viewModel(),
  setHistoryList: (Flow<PagingData<SetRecord>>) -> Unit,
  setContextMenu: (@Composable RowScope.() -> Unit) -> Unit
) {
  val (index, setIndex) = remember { mutableStateOf(0) }
  val instructions by model.exercises.collectAsState(initial = emptyList(), Dispatchers.IO)
  val instruction by derivedStateOf { instructions.getOrNull(index) }
  val exerciseSet by instruction?.set?.collectAsState(initial = null, Dispatchers.IO)
    ?: remember { mutableStateOf<ExerciseSet?>(null) }
  val isRefreshing by model.isLoading.collectAsState()

  val allRecords by model.records.collectAsState(initial = emptyList())
  val setRecords = recordsByExerciseId(allRecords = allRecords)
  val currentSetRecord = exerciseSet?.let { setRecords[it.id] }

  LaunchedEffect(exerciseSet) {
    setContextMenu { instruction?.let { this.ExerciseContextMenu(it) } }
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
    Column {
      DetailView(
        index = 0,
        maxIndex = 2,
        setWithRecord = ExerciseSetWithRecord(
          exerciseSet,
          Record(25.0, exerciseSet.reps, exerciseSet),
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
        ExerciseDetails(setWithRecord?.exerciseSet, Modifier.weight(1f))
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
          ExerciseDetails(setWithRecord?.exerciseSet)
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

@Preview(showBackground = true)
@Composable
fun PreviewExerciseDetails(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  MaterialTheme(Theme.darkColors) {
    Column {
      this.ExerciseDetails(
        exerciseSet = exerciseSet
      )
    }
  }
}

@Composable
private fun RowScope.ExerciseDetails(
  exerciseSet: ExerciseSet?,
  modifier: Modifier = Modifier
) {
  Column(modifier) {
    ExerciseDetails(exerciseSet)
  }
}

@Composable
private fun ColumnScope.ExerciseDetails(
  exerciseSet: ExerciseSet?
) {
  Row {
    Text(text = exerciseSet?.exerciseName ?: "", style = MaterialTheme.typography.h6)
  }
  Row(Modifier.padding(vertical = 5.dp)) {
    Column(Modifier.weight(1f)) {
      val label = stringResource(id = R.string.target_reps)
      val target = stringResource(id = R.string.target)
      val toFailureLabel = stringResource(id = R.string.to_failure)
      when {
        exerciseSet == null -> Text("")
        exerciseSet.reps < 0 -> Text("$label $toFailureLabel")
        exerciseSet.repsUnit.isNotBlank() && exerciseSet.repsRange > 0 && !exerciseSet.isToFailure -> Text(
          "$target ${exerciseSet.reps}-${exerciseSet.reps + exerciseSet.repsRange} ${exerciseSet.repsUnit}"
        )
        exerciseSet.repsUnit.isNotBlank() && exerciseSet.repsRange > 0 && exerciseSet.isToFailure -> Text(
          "$target ${exerciseSet.reps}-${exerciseSet.reps + exerciseSet.repsRange} ${exerciseSet.repsUnit} ($toFailureLabel)"
        )
        exerciseSet.repsUnit.isNotBlank() && !exerciseSet.isToFailure -> Text("$target ${exerciseSet.reps} ${exerciseSet.repsUnit}")
        exerciseSet.repsUnit.isNotBlank() && exerciseSet.isToFailure -> Text("$target ${exerciseSet.reps} ${exerciseSet.repsUnit} ($toFailureLabel)")
        exerciseSet.repsRange > 0 && !exerciseSet.isToFailure -> Text("$label ${exerciseSet.reps}-${exerciseSet.reps + exerciseSet.repsRange}")
        exerciseSet.repsRange > 0 && exerciseSet.isToFailure -> Text("$label ${exerciseSet.reps}-${exerciseSet.reps + exerciseSet.repsRange} ($toFailureLabel)")
        exerciseSet.isToFailure -> Text("$label ${exerciseSet.reps} ($toFailureLabel)")
        else -> Text("$label ${exerciseSet.reps}")
      }
    }
    Column(Modifier.weight(1f)) {
      val label = stringResource(id = R.string.target_sets)
      val toCompletion = stringResource(id = R.string.sets_to_completion)
      if (exerciseSet == null) Text("")
      else if (exerciseSet.sets < 0) Text(toCompletion)
      else Text("$label ${exerciseSet.sets}")
    }
  }
  val scrollState = rememberScrollState()
  Row() {
    // TODO is there a way to show the scrollbar to indicate scrollability?
    // FIXME does not work if there is no content below
    Column(Modifier.verticalScroll(scrollState)) {
      Row(Modifier.padding(vertical = 5.dp)) {
        if (exerciseSet != null) {
          val exercise by exerciseSet.exercise.collectAsState(null, Dispatchers.IO)
          Text(exercise?.description ?: "")
        }
      }
      Row(
        Modifier
          .padding(vertical = 5.dp)
          .fillMaxHeight()
      ) {
        Text(exerciseSet?.note ?: "")
      }
    }
  }
}

@Composable
fun RowScope.ExerciseContextMenu(instruction: ExerciseViewModel.ExerciseInstruction) {
  if (instruction.hasAlternate) {
    val (alerted, setAlerted) = remember { mutableStateOf(false) }
    if (alerted) {
      AlertDialog(onDismissRequest = { setAlerted(false) },
        title = { Text("Alternate Exercises") },
        text = { Text("Select from alternate exercises") },
        buttons = {
          val activeIndex by instruction.activeIndex.collectAsState()
          LazyColumn(Modifier.padding(bottom = 10.dp))
          {
            itemsIndexed(instruction.sets) { index, set ->
              Row(
                Modifier
                  .padding(horizontal = 5.dp)
                  .fillParentMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                val onClick = {
                  instruction.activateAlternate(index)
                  setAlerted(false)
                }
                RadioButton(
                  selected = index == activeIndex,
                  onClick = onClick
                )
                Text(set.exerciseName, Modifier.clickable(onClick = onClick))
              }
            }
          }
        })
    }
    Column(Modifier
      .clickable {
        if (instruction.alternateCount > 2) setAlerted(true)
        else instruction.activateNextAlternate()
      }
      .fillMaxHeight(),
      verticalArrangement = Arrangement.Center) {
      val altLabel = stringResource(id = R.string.alternate)
      Text(altLabel, Modifier.padding(end = 5.dp))
    }
  }
}
