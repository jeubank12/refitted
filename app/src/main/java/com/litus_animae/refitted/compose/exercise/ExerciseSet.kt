package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arrow.core.getOrElse
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.compose.state.Repetitions
import com.litus_animae.refitted.compose.state.Weight
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.Record
import com.litus_animae.refitted.util.MonadUtil.optionWhen
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
  Column(modifier.padding(16.dp)) {
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
  val timerMillis = rememberSaveable { mutableStateOf(0L) }
  val saveWeight by weight.value
  val saveReps by reps.value

  Row(Modifier.weight(3f)) {
    Column(Modifier.weight(1f)) {
      Card(
        Modifier
          .padding(bottom = 5.dp)
          .weight(1f)
      ) {
        WeightDisplay(onStartEditWeight, weight, saveWeight)
      }
      Card(
        Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        SetsDisplay(exerciseSet, numCompleted, record)
      }
    }
    Column(Modifier.weight(1f)) {
      RepsDisplay(setWithRecord, reps)
    }
  }
  Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
    Column(
      Modifier
        .weight(1f)
        .fillMaxWidth()
    ) {
      val enabled = currentIndex > 0
      Button(
        onClick = {
          updateIndex(
            currentIndex - 1,
            record.copy(weight = saveWeight, reps = saveReps)
          )
        },
        enabled = enabled
      ) {
        val text = stringResource(id = R.string.move_left)
        Text(text)
      }
    }
    Column(
      Modifier.weight(3f),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      val localRestFormat = stringResource(R.string.seconds_rest_phrase)
      // FIXME this doesn't seem to be updating when changing exercise sets, maybe derived state is unnecessary
      val timerDisplayTime by remember {
        derivedStateOf {
          if (timerRunning.value) String.format(localRestFormat, timerMillis.value / 1000f)
          else String.format(localRestFormat, exerciseSet.rest.toFloat())
        }
      }
      Text(timerDisplayTime, style = MaterialTheme.typography.h4)
    }
    Column(
      Modifier
        .weight(1f)
        .fillMaxWidth()
    ) {
      val enabled = currentIndex < maxIndex
      Button(
        onClick = {
          updateIndex(
            currentIndex + 1,
            record.copy(weight = saveWeight, reps = saveReps)
          )
        },
        enabled = enabled
      ) {
        val text = stringResource(id = R.string.move_right)
        Text(text)
      }
    }
  }
  Row(Modifier.weight(1f)) {
    Column {
      val isTimerRunning by timerRunning
      Timer(isTimerRunning,
        millisToElapse = exerciseSet.rest * 1000L,
        countDown = true,
        onUpdate = { timerMillis.value = it }) { timerRunning.value = false }
      Button(
        onClick = {
          if (!isTimerRunning) {
            onSave(record.copy(weight = saveWeight, reps = saveReps))
            timerMillis.value = exerciseSet.rest * 1000L
          }
          if (exerciseSet.rest > 0 || isTimerRunning) timerRunning.value = !isTimerRunning
        },
        Modifier.fillMaxWidth(),
        enabled = setWithRecord.exerciseIncomplete
      ) {
        val cancelRestPhrase = stringResource(id = R.string.cancel_rest)
        val exerciseCompletePhrase = stringResource(id = R.string.complete_exercise)
        val toCompletionSetPhrase = optionWhen(exerciseSet.sets < 0) {
          if (exerciseSet.reps(numCompleted) < 0)
            String.format(
              pluralStringResource(id = R.plurals.complete_reps, count = saveReps),
              saveReps
            )
          else String.format(
            pluralStringResource(id = R.plurals.complete_reps_of_workout, count = saveReps),
            saveReps
          )
        }
        val completeSetPhrase = toCompletionSetPhrase
          .getOrElse {
            exerciseSet.superSetStep.fold({
              String.format(
                stringResource(id = R.string.complete_set_of_workout),
                numCompleted + 1
              )
            }) {
              String.format(
                pluralStringResource(
                  id = R.plurals.complete_superset_part_x,
                  count = exerciseSet.sets
                ),
                it + 1
              )
            }
          }
        val setText =
          if (setWithRecord.exerciseIncomplete) completeSetPhrase
          else exerciseCompletePhrase
        val buttonText = if (isTimerRunning) cancelRestPhrase else setText
        Text(buttonText, style = MaterialTheme.typography.h5)
      }
    }
  }
}

@Composable
@Preview(heightDp = 400)
fun PreviewExerciseSetDetails() {
  var numCompleted by remember { mutableStateOf(0) }
  var currentIndex by remember { mutableStateOf(5) }
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
