package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@Composable
fun ExerciseInstructions(
  setWithRecord: ExerciseSetWithRecord?,
  modifier: Modifier = Modifier
) {
  Column(modifier.padding(16.dp)) {
    ExerciseInstructions(setWithRecord)
  }
}

@Composable
fun ColumnScope.ExerciseInstructions(
  setWithRecord: ExerciseSetWithRecord?
) {
  val exerciseSet = setWithRecord?.exerciseSet
  Row {
    Text(text = exerciseSet?.exerciseName ?: "", style = MaterialTheme.typography.h4)
  }
  Row(Modifier.padding(top = 5.dp)) {
    val label = stringResource(id = R.string.target_reps)
    val target = stringResource(id = R.string.target)
    val toFailureLabel = stringResource(id = R.string.to_failure)
    val targetRepsTextContent = when {
      setWithRecord == null || exerciseSet == null -> ""
      setWithRecord.reps < 0 -> "$label $toFailureLabel"
      exerciseSet.repsUnit.isNotBlank() && exerciseSet.repsRange > 0 && !exerciseSet.isToFailure ->
        "$target ${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange} ${exerciseSet.repsUnit}"

      exerciseSet.repsUnit.isNotBlank() && exerciseSet.repsRange > 0 && exerciseSet.isToFailure ->
        "$target ${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange} ${exerciseSet.repsUnit} ($toFailureLabel)"

      exerciseSet.repsUnit.isNotBlank() && !exerciseSet.isToFailure -> "$target ${setWithRecord.reps} ${exerciseSet.repsUnit}"
      exerciseSet.repsUnit.isNotBlank() && exerciseSet.isToFailure -> "$target ${setWithRecord.reps} ${exerciseSet.repsUnit} ($toFailureLabel)"
      exerciseSet.repsRange > 0 && !exerciseSet.isToFailure -> "$label ${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange}"
      exerciseSet.repsRange > 0 && exerciseSet.isToFailure -> "$label ${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange} ($toFailureLabel)"
      exerciseSet.isToFailure -> "$label ${setWithRecord.reps} ($toFailureLabel)"
      else -> "$label ${setWithRecord.reps}"
    }
    Text(targetRepsTextContent, style = MaterialTheme.typography.h5)
  }
  Row(Modifier.padding(top = 5.dp)) {
    val label = stringResource(id = R.string.target_sets)
    val toCompletion = stringResource(id = R.string.sets_to_completion)
    val targetSetsTextContent = when {
      exerciseSet == null -> ""
      exerciseSet.sets < 0 -> toCompletion
      else -> "$label ${exerciseSet.sets}"
    }
    Text(targetSetsTextContent, style = MaterialTheme.typography.h6)
  }

  val scrollState = rememberScrollState()
  Row(Modifier.weight(5f, fill = true).padding(top = 5.dp)) {
    // TODO is there a way to show the scrollbar to indicate scrollability?
    Column(Modifier.verticalScroll(scrollState)) {
      Row(
        Modifier
          .defaultMinSize(minHeight = 5.dp)
          .fillMaxWidth()
      ) {
        if (exerciseSet != null) {
          val exercise by exerciseSet.exercise.collectAsState(null, Dispatchers.IO)
          Text(exercise?.description ?: "", Modifier.padding(bottom = 5.dp))
        }
      }
      Row(
        Modifier
          .padding(vertical = 5.dp)
          .fillMaxWidth()
          .defaultMinSize(minHeight = 30.dp)
      ) {
        Text(exerciseSet?.note ?: "")
      }
    }
  }
  val exerciseTimerRunning = rememberSaveable { mutableStateOf(false) }
  val exerciseTimerMillis = rememberSaveable { mutableStateOf(0L) }
  val isExerciseTimerRunning by exerciseTimerRunning
  Timer(isExerciseTimerRunning,
    millisToElapse = exerciseSet?.timeLimitMilliseconds ?: 0,
    countDown = true,
    animateTimer = false,
    onUpdate = { exerciseTimerMillis.value = it }) { exerciseTimerRunning.value = false }
  if (exerciseSet?.timeLimitMilliseconds != null) {
    Row(Modifier.weight(1f)) {
      Button(
        onClick = {
          if (!isExerciseTimerRunning) {
            exerciseTimerMillis.value = exerciseSet.timeLimitMilliseconds
          }
          exerciseTimerRunning.value = !isExerciseTimerRunning
        },
        Modifier.fillMaxWidth()
      ) {
        val timerValue = Instant.ofEpochMilli(exerciseTimerMillis.value)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        val timerMax = Instant.ofEpochMilli(exerciseSet.timeLimitMilliseconds)
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("m:ss"))
        if (isExerciseTimerRunning) Text("$timerValue remaining (click to stop)")
        else Text("Start $timerMax exercise timer")
      }
    }
  }
}