package com.litus_animae.refitted.ui.compose.exercise.set

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.identity.ConfigProvider
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.LocalFeatures
import com.litus_animae.refitted.ui.compose.exercise.CircularRestTimer
import com.litus_animae.refitted.ui.compose.exercise.RepsDisplay
import com.litus_animae.refitted.ui.compose.exercise.RepsDisplayEditingMinHeight
import com.litus_animae.refitted.ui.compose.exercise.RepsDisplayMinHeight
import com.litus_animae.refitted.ui.compose.exercise.TargetStepper
import com.litus_animae.refitted.ui.compose.exercise.WeightDisplay
import com.litus_animae.refitted.ui.compose.exercise.exampleExerciseSet
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.state.Repetitions
import com.litus_animae.refitted.ui.compose.state.Weight
import com.litus_animae.refitted.ui.compose.util.Theme
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import com.litus_animae.refitted.data.models.Record
import com.litus_animae.refitted.data.models.SetRecord
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.Instant

// Wrapped-content height of the sets TargetStepper card (48.dp IconButton row + overline
// label + the surrounding 8.dp padding on each side) - pinned rather than measured so the
// strip's height budget below can be computed exactly instead of guessed.
private val EditStepperCardHeight = 80.dp
// Raised from 72/48.dp to fit the strip's title + zone-label header row on top of the chart.
private val StripPreferredHeight = 88.dp
private val StripMinHeight = 64.dp

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
  restOverride: Int? = null,
  onRestOverrideChange: ((Int) -> Unit)? = null,
  nextRestSeconds: Int? = null,
  editing: Boolean = false,
  onUpdateCustomTargets: ((sets: Int, reps: Int, repsRange: Int) -> Unit)? = null,
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
      restOverride = restOverride,
      onRestOverrideChange = onRestOverrideChange,
      nextRestSeconds = nextRestSeconds,
      editing = editing,
      onUpdateCustomTargets = onUpdateCustomTargets,
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
  restOverride: Int? = null,
  onRestOverrideChange: ((Int) -> Unit)? = null,
  nextRestSeconds: Int? = null,
  editing: Boolean = false,
  onUpdateCustomTargets: ((sets: Int, reps: Int, repsRange: Int) -> Unit)? = null,
) {
  val exerciseSet = setWithRecord.exerciseSet
  val currentRecord = setWithRecord.currentRecord
  val numCompleted = setWithRecord.numCompleted
  val record by currentRecord
  // Keyed on exerciseSet.id (day.step), not the exerciseSet object itself - editing this set's
  // own target/rest (see onUpdateCustomTargets/onRestOverrideChange) replaces that object via
  // .copy(), and keying on the whole value would reset an in-progress weight/reps pick every
  // time. record is still a key: a genuinely new record (next set, different day) should reset.
  val weight = rememberSaveable(saver = Weight.Saver, inputs = arrayOf(exerciseSet.id, record)) {
    Weight(record.weight)
  }
  val reps = rememberSaveable(
    saver = Repetitions.Saver,
    inputs = arrayOf(exerciseSet.id, record)
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
        Card(Modifier.fillMaxWidth(), elevation = 2.dp) {
          WeightDisplay(onStartEditWeight, weight, saveWeight)
        }
        Card(Modifier.fillMaxWidth(), elevation = 2.dp) {
          RepsDisplay(
            setWithRecord,
            reps,
            editing = editing,
            onUpdateTargetReps = onUpdateCustomTargets?.let { update ->
              { newReps: Int, newRepsRange: Int -> update(exerciseSet.sets, newReps, newRepsRange) }
            }
          )
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
      val repsFloor = if (editing && onUpdateCustomTargets != null) {
        RepsDisplayEditingMinHeight
      } else {
        RepsDisplayMinHeight
      }
      val repsHeight = maxOf(available / 2, repsFloor.roundToPx())
        .coerceAtMost(available)
      val weightPlaceable = weightCard.measure(Constraints.fixed(width, available - repsHeight))
      val repsPlaceable = repsCard.measure(Constraints.fixed(width, repsHeight))
      layout(width, constraints.maxHeight) {
        weightPlaceable.place(0, 0)
        repsPlaceable.place(0, weightPlaceable.height + spacing)
      }
    }
    BoxWithConstraints(
      Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(start = 8.dp),
      contentAlignment = Alignment.Center
    ) {
      val stepperShown = editing && onUpdateCustomTargets != null
      val stepperHeight = if (stepperShown) EditStepperCardHeight + 8.dp else 0.dp
      val timerHeight = RepsDisplayMinHeight + 16.dp
      // No scroll exists in this pane, so a strip that doesn't fit is omitted rather than
      // shrunk below legibility or wrapped - landscape and short screens fall through to
      // stripHeight == null and the layout is unchanged from before the strip existed.
      val stripHeight = if (maxHeight == Dp.Infinity) {
        null
      } else {
        val slack = maxHeight - timerHeight - stepperHeight - 8.dp
        when {
          slack >= StripPreferredHeight -> StripPreferredHeight
          slack >= StripMinHeight -> slack
          else -> null
        }
      }
      val showStrip = stripHeight != null &&
        LocalFeatures.current.flags[ConfigProvider.Companion.Feature.RECORD_CHART_TYPE] == "effort"

      // Wraps its content instead of filling the row's height (which the Weight/Reps
      // stack next to it doesn't fully occupy either) so it floats centered rather than
      // stretching to an arbitrary bottom edge that never quite matched theirs.
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showStrip) {
          SetTrendStrip(
            Modifier
              .fillMaxWidth()
              .height(stripHeight),
            history = setWithRecord.recentSets,
            todaysRecords = setWithRecord.setRecords
          )
        }
        if (stepperShown) {
          Card(Modifier.fillMaxWidth().height(EditStepperCardHeight), elevation = 2.dp) {
            Box(Modifier.padding(8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
              // TODO localize
              TargetStepper(
                label = "sets",
                value = exerciseSet.sets,
                onChange = { onUpdateCustomTargets(it, exerciseSet.reps, exerciseSet.repsRange) }
              )
            }
          }
        }
        Card(Modifier.fillMaxWidth(), elevation = 2.dp) {
          CircularRestTimer(
            restSeconds = effectiveRestSeconds,
            isRunning = isTimerRunning,
            startedAt = effectiveTimerStart,
            // Grows the card instead of insetting the ring - CircularRestTimer's own square
            // fit (ringDp = min(w,h) - 16.dp) already reserves that same 16dp as its
            // horizontal margin, so matching top/bottom to it means giving the *box* 16dp
            // more height, not padding down the ring's existing (correct) size.
            modifier = Modifier.fillMaxWidth().height(RepsDisplayMinHeight + 16.dp),
            nextRestSeconds = nextRestSeconds,
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

private val previewRecentSets: List<SetRecord> = run {
  val base = Instant.now().minus(Duration.ofDays(10))
  (0 until 10).flatMap { day ->
    (0 until 3).map { setIndex ->
      SetRecord(
        weight = 95.0 + day * 1.5,
        reps = 8,
        workout = exampleExerciseSet.workout,
        targetSet = exampleExerciseSet.id,
        completed = base.plus(Duration.ofDays(day.toLong())).plusSeconds(setIndex * 120L),
        exercise = exampleExerciseSet.exerciseName
      )
    }
  }
}

@OptIn(FlowPreview::class)
@Composable
private fun PreviewExerciseSetDetails(
  heightDp: Int = 400,
  editing: Boolean = false,
  recentSets: List<SetRecord> = previewRecentSets
) {
  var numCompleted by remember { mutableIntStateOf(0) }
  var currentIndex by remember { mutableIntStateOf(5) }
  var sets by remember { mutableIntStateOf(exampleExerciseSet.sets) }
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
  CompositionLocalProvider(
    LocalFeatures provides ConfigProvider.Companion.RemoteConfig(
      mapOf(ConfigProvider.Companion.Feature.RECORD_CHART_TYPE to "effort")
    )
  ) {
    MaterialTheme(Theme.lightColors) {
      Column(
        Modifier
          .padding(16.dp)
          .height(heightDp.dp)
          .fillMaxWidth()
      ) {
        ExerciseSetView(
          setWithRecord = ExerciseSetWithRecord(
            exampleExerciseSet.copy(sets = sets),
            currentRecord,
            numCompleted = 1,
            setRecords = records,
            allSets = emptyFlow(),
            recentSets = flowOf(recentSets)
          ),
          currentIndex = currentIndex,
          maxIndex = 5,
          updateIndex = { newIndex, _ -> currentIndex = newIndex },
          onSave = { numCompleted += 1 },
          onStartEditWeight = {},
          showNavigationButtons = false,
          editing = editing,
          onUpdateCustomTargets = if (editing) {
            { newSets, _, _ -> sets = newSets }
          } else {
            null
          }
        )
      }
    }
  }
}

@Preview(heightDp = 400, apiLevel = 36)
@Composable
private fun PreviewExerciseSetDetailsStripShown() {
  PreviewExerciseSetDetails(heightDp = 400)
}

@Preview(heightDp = 300, apiLevel = 36)
@Composable
private fun PreviewExerciseSetDetailsStripOmitted() {
  PreviewExerciseSetDetails(heightDp = 300)
}

@Preview(heightDp = 400, apiLevel = 36)
@Composable
private fun PreviewExerciseSetDetailsEditing() {
  PreviewExerciseSetDetails(heightDp = 400, editing = true)
}
