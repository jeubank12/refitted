package com.litus_animae.refitted.compose.exercise.set

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.exercise.RepsDisplay
import com.litus_animae.refitted.compose.exercise.SetsDisplay
import com.litus_animae.refitted.compose.exercise.WeightDisplay
import com.litus_animae.refitted.compose.exercise.exampleExerciseSet
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.compose.state.Repetitions
import com.litus_animae.refitted.compose.state.Weight
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.Record
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

@Composable
fun ExerciseSetView(
  setWithRecord: ExerciseSetWithRecord,
  currentIndex: Int,
  maxIndex: Int,
  updateIndex: (Int, Record) -> Unit,
  onSave: (Record) -> Unit,
  onStartEditWeight: (Weight) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    ExerciseSetView(
      setWithRecord = setWithRecord,
      currentIndex = currentIndex,
      maxIndex = maxIndex,
      updateIndex = updateIndex,
      onSave = onSave,
      onStartEditWeight = onStartEditWeight
    )
  }
}

@Composable
fun ColumnScope.ExerciseSetView(
  setWithRecord: ExerciseSetWithRecord,
  currentIndex: Int,
  maxIndex: Int,
  updateIndex: (Int, Record) -> Unit,
  onSave: (Record) -> Unit,
  onStartEditWeight: (Weight) -> Unit
) {
  val (exerciseSet, currentRecord, numCompleted, _, _) = setWithRecord
  val record by currentRecord
  val weight = rememberSaveable(saver = Weight.Saver, inputs = arrayOf(exerciseSet, record)) {
    Weight(record.weight)
  }
  val reps = rememberSaveable(
    saver = Repetitions.Saver,
    inputs = arrayOf(exerciseSet, record)
  ) { Repetitions(if (exerciseSet.repsAreSequenced) setWithRecord.reps else record.reps) }

  val timerRunning = rememberSaveable { mutableStateOf(false) }
  val timerDuration = rememberSaveable { mutableIntStateOf(exerciseSet.rest * 1000) }
  LaunchedEffect(timerRunning.value, exerciseSet) {
    if (!timerRunning.value) {
      timerDuration.intValue = exerciseSet.rest * 1000
    }
  }
  val timerStart = rememberSaveable { mutableStateOf(Instant.now()) }

  val saveWeight by weight.value
  val saveReps by reps.value

  Row(Modifier.weight(3f)) {
    Column(
      Modifier
        .weight(1f)
        .padding(end = 8.dp)
    ) {
      Card(
        Modifier
          .padding(bottom = 8.dp)
          .weight(1f)
      ) {
        WeightDisplay(onStartEditWeight, weight, saveWeight)
      }
      Card(
        Modifier
          .fillMaxWidth()
          .padding(top = 8.dp)
          .weight(1f)
      ) {
        SetsDisplay(exerciseSet, numCompleted, record)
      }
    }
    Column(
      Modifier
        .weight(1f)
        .padding(start = 8.dp)
    ) {
      RepsDisplay(setWithRecord, reps)
    }
  }
  val isTimerRunning by timerRunning
  Row(
    Modifier.height(80.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    val moveLeftEnabled = currentIndex > 0
    Button(
      onClick = {
        updateIndex(
          currentIndex - 1,
          record.copy(weight = saveWeight, reps = saveReps)
        )
      },
      enabled = moveLeftEnabled
    ) {
      val text = stringResource(id = R.string.move_left)
      Text(text)
    }

    RestTimer(
      Modifier
        .padding(horizontal = 15.dp)
        .weight(3f),
      isTimerRunning,
      timerDuration.intValue,
      timerStart.value,
      timerRunning,
      exerciseSet
    ) {
      timerRunning.value = false
      timerDuration.intValue = exerciseSet.rest * 1000
    }


    val moveRightEnabled = currentIndex < maxIndex
    Button(
      onClick = {
        updateIndex(
          currentIndex + 1,
          record.copy(weight = saveWeight, reps = saveReps)
        )
      },
      enabled = moveRightEnabled
    ) {
      val text = stringResource(id = R.string.move_right)
      Text(text)
    }
  }

  CompleteExerciseSetButton(
    Modifier,
    onClick = {
      if (!isTimerRunning) {
        onSave(record.copy(weight = saveWeight, reps = saveReps))
      }
      if (exerciseSet.rest > 0 || isTimerRunning) {
        timerRunning.value = !isTimerRunning
        timerStart.value = Instant.now()
      }
    },
    setWithRecord.exerciseIncomplete,
    exerciseSet.sets,
    exerciseSet.reps(numCompleted),
    saveReps,
    exerciseSet.superSetStep,
    numCompleted,
    isTimerRunning
  )
}

@Composable
@Preview(heightDp = 400)
private fun PreviewExerciseSetDetails() {
  var numCompleted by remember { mutableIntStateOf(0) }
  var currentIndex by remember { mutableIntStateOf(5) }
  val records = remember { mutableStateListOf<Record>() }
  val currentRecord =
    remember {
      mutableStateOf(
        Record(
          25.0,
          exampleExerciseSet.reps(0),
          exampleExerciseSet,
          Instant.now()
        )
      )
    }
  MaterialTheme(Theme.lightColors) {
    Column(
      Modifier
        .padding(16.dp)
        .fillMaxSize()
    ) {
      ExerciseSetView(
        setWithRecord = ExerciseSetWithRecord(
          exampleExerciseSet,
          currentRecord,
          numCompleted = 1,
          setRecords = records,
          allSets = emptyFlow()
        ),
        currentIndex = currentIndex,
        maxIndex = 5,
        updateIndex = { newIndex, _ -> currentIndex = newIndex },
        onSave = { numCompleted += 1 },
        onStartEditWeight = {}
      )
    }
  }
}
