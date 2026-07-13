package com.litus_animae.refitted.ui.compose.exercise.set

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.exercise.CircularRestTimer
import com.litus_animae.refitted.ui.compose.exercise.RepsDisplay
import com.litus_animae.refitted.ui.compose.exercise.RepsDisplayMinHeight
import com.litus_animae.refitted.ui.compose.exercise.WeightDisplay
import com.litus_animae.refitted.ui.compose.exercise.exampleExerciseSet
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.state.Repetitions
import com.litus_animae.refitted.ui.compose.state.Weight
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import com.litus_animae.refitted.data.models.Record
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

/**
 * Wrapper overload that puts [ExerciseSetView] in its own [Column].
 * Used by the legacy [ExerciseView] path.
 */
@OptIn(FlowPreview::class)
@Composable
fun ExerciseSetView(
  setWithRecord: ExerciseSetWithRecord,
  currentIndex: Int,
  maxIndex: Int,
  updateIndex: (Int, Record) -> Unit,
  onSave: (Record) -> Unit,
  onStartEditWeight: (Weight) -> Unit,
  modifier: Modifier = Modifier,
  showNavigationButtons: Boolean = true,
  externalTimerState: ExerciseViewModel.TimerState? = null,
  onTimerToggle: (() -> Unit)? = null,
  maxRestSeconds: Int = setWithRecord.exerciseSet.rest,
  restOverride: Int? = null,
  onRestOverrideChange: ((Int) -> Unit)? = null,
) {
  Column(modifier) {
    ExerciseSetView(
      setWithRecord = setWithRecord,
      currentIndex = currentIndex,
      maxIndex = maxIndex,
      updateIndex = updateIndex,
      onSave = onSave,
      onStartEditWeight = onStartEditWeight,
      showNavigationButtons = showNavigationButtons,
      externalTimerState = externalTimerState,
      onTimerToggle = onTimerToggle,
      maxRestSeconds = maxRestSeconds,
      restOverride = restOverride,
      onRestOverrideChange = onRestOverrideChange,
    )
  }
}

@OptIn(FlowPreview::class)
@Composable
fun ColumnScope.ExerciseSetView(
  setWithRecord: ExerciseSetWithRecord,
  currentIndex: Int,
  maxIndex: Int,
  updateIndex: (Int, Record) -> Unit,
  onSave: (Record) -> Unit,
  onStartEditWeight: (Weight) -> Unit,
  showNavigationButtons: Boolean = true,
  externalTimerState: ExerciseViewModel.TimerState? = null,
  onTimerToggle: (() -> Unit)? = null,
  maxRestSeconds: Int = setWithRecord.exerciseSet.rest,
  restOverride: Int? = null,
  onRestOverrideChange: ((Int) -> Unit)? = null,
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

  // Local timer state used as fallback for the legacy ExerciseView path
  val timerRunning = rememberSaveable { mutableStateOf(false) }
  val timerDuration = rememberSaveable { mutableIntStateOf(exerciseSet.rest * 1000) }
  LaunchedEffect(timerRunning.value, exerciseSet) {
    if (!timerRunning.value) {
      timerDuration.intValue = exerciseSet.rest * 1000
    }
  }
  val timerStart = rememberSaveable { mutableStateOf(Instant.now()) }

  // Effective timer state — use external (ViewModel-held, sticky) when provided
  val isTimerRunning = externalTimerState?.isRunning ?: timerRunning.value
  val effectiveTimerStart = externalTimerState?.startedAt ?: timerStart.value
  val effectiveRestSeconds = restOverride ?: exerciseSet.rest

  val saveWeight by weight.value
  val saveReps by reps.value

  // Controls row: left = Weight + Reps | right = CircularRestTimer
  Row(Modifier.weight(3f)) {
    // Split the height evenly between the two cards, except the reps card may
    // not shrink below its own minimum (the 170.dp inside RepsDisplay) —
    // when space is tight the weight card absorbs the difference
    Layout(
      content = {
        Card(Modifier.fillMaxWidth()) {
          WeightDisplay(onStartEditWeight, weight, saveWeight)
        }
        Card(Modifier.fillMaxWidth()) {
          RepsDisplay(setWithRecord, reps)
        }
      },
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(end = 8.dp, bottom = 8.dp)
    ) { measurables, constraints ->
      val spacing = 8.dp.roundToPx()
      val width = constraints.maxWidth
      val available = (constraints.maxHeight - spacing).coerceAtLeast(0)
      val (weightCard, repsCard) = measurables
      val repsHeight = maxOf(available / 2, RepsDisplayMinHeight.roundToPx())
        .coerceAtMost(available)
      val weightPlaceable = weightCard.measure(Constraints.fixed(width, available - repsHeight))
      val repsPlaceable = repsCard.measure(Constraints.fixed(width, repsHeight))
      layout(width, constraints.maxHeight) {
        weightPlaceable.place(0, 0)
        repsPlaceable.place(0, weightPlaceable.height + spacing)
      }
    }
    Column(
      Modifier
        .weight(1f)
        .padding(start = 8.dp)
    ) {
      CircularRestTimer(
        restSeconds = effectiveRestSeconds,
        maxRestSeconds = maxRestSeconds,
        isRunning = isTimerRunning,
        startedAt = effectiveTimerStart,
        onAdjust = onRestOverrideChange,
        onFinish = {
          if (onTimerToggle != null) {
            onTimerToggle()
          } else {
            timerRunning.value = false
            timerDuration.intValue = exerciseSet.rest * 1000
          }
        }
      )
    }
  }

  // Navigation row — kept for the legacy ExerciseView path, hidden in pager path
  if (showNavigationButtons) {
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
  }

  CompleteExerciseSetButton(
    Modifier,
    onClick = {
      if (!isTimerRunning) {
        onSave(record.copy(weight = saveWeight, reps = saveReps))
      }
      if (effectiveRestSeconds > 0 || isTimerRunning) {
        if (onTimerToggle != null) {
          onTimerToggle()
        } else {
          timerRunning.value = !isTimerRunning
          timerStart.value = Instant.now()
        }
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

@OptIn(FlowPreview::class)
@Composable
@Preview(heightDp = 400, apiLevel = 36)
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
  androidx.compose.material.MaterialTheme(Theme.lightColors) {
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
        onStartEditWeight = {},
        showNavigationButtons = false
      )
    }
  }
}
