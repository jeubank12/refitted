package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.ui.compose.state.Repetitions
import com.litus_animae.refitted.ui.compose.util.NumberPicker
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.Record
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

/** Minimum height [RepsDisplay] needs; layouts sizing its container must not go below this */
val RepsDisplayMinHeight = 170.dp

/** Minimum height needed in edit mode, where [RepsRangeStepper] adds a value line and a +/- row */
val RepsDisplayEditingMinHeight = 200.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepsDisplay(
  setWithRecord: ExerciseSetWithRecord,
  reps: Repetitions,
  editing: Boolean = false,
  onUpdateTargetReps: ((reps: Int, repsRange: Int) -> Unit)? = null
) {
  val exerciseSet = setWithRecord.exerciseSet
  val targetUpdater = onUpdateTargetReps.takeIf { editing }
  Column(
    Modifier
      .padding(bottom = 5.dp)
      .heightIn(min = if (targetUpdater != null) RepsDisplayEditingMinHeight else RepsDisplayMinHeight),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {

    val (targetRepsTextContent, subtext) = when {
      setWithRecord.reps < 0 -> "MAX" to ""
      exerciseSet.repsUnit.isNotBlank() && exerciseSet.repsRange > 0 && !exerciseSet.isToFailure ->
        "${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange}" to ""

      exerciseSet.repsUnit.isNotBlank() && exerciseSet.repsRange > 0 && exerciseSet.isToFailure ->
        "${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange}" to "(or to failure)"

      exerciseSet.repsUnit.isNotBlank() && !exerciseSet.isToFailure -> "${setWithRecord.reps}" to ""

      exerciseSet.repsUnit.isNotBlank() && exerciseSet.isToFailure -> "${setWithRecord.reps}" to "(or to failure)"

      exerciseSet.repsRange > 0 && !exerciseSet.isToFailure -> "${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange}" to ""
      exerciseSet.repsRange > 0 && exerciseSet.isToFailure -> "${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange}" to "(to failure)"
      exerciseSet.isToFailure -> "${setWithRecord.reps}" to "(to failure)"
      else -> "${setWithRecord.reps}" to ""
    }

    // Shared by the NumberPicker digits, the static target-reps text, and RepsRangeStepper
    // below - all three need to match, not just individually look right.
    val typography = MaterialTheme.typography.displayMedium
    val currentRepsValue by reps.value

    Spacer(Modifier.weight(1f))
    NumberPicker(
      pageCount = 101,
      initialPage = currentRepsValue,
      pageWidth = 80.dp,
      typography = typography,
      modifier = Modifier
        .heightIn(min = 50.dp)
        .fillMaxWidth()
    ) {
      reps.set(it)
    }
    val lineColor = contentColorFor(MaterialTheme.colorScheme.surface)
    HorizontalDivider(
      Modifier.width(70.dp),
      color = lineColor,
      thickness = 3.dp
    )
    Row(Modifier.fillMaxWidth()) {
      // Edit mode: adjustable target reps in place of the static prescription text.
      // isToFailure/repsUnit/sequenced reps are admin-only and never occur on a custom set, but
      // a rep range is - hence RepsRangeStepper rather than the plain TargetStepper the sets
      // count next to it uses.
      if (targetUpdater != null) {
        // Matches the NumberPicker's own text style above - the "Reps" header below already
        // labels this, so the stepper's own caption would just repeat it.
        RepsRangeStepper(
          setId = exerciseSet.id,
          reps = exerciseSet.reps,
          repsRange = exerciseSet.repsRange,
          onChange = targetUpdater,
          valueStyle = typography
        )
      } else {
        Column(
          Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.Top,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(targetRepsTextContent, style = typography)
          AnimatedVisibility(
            visible = subtext.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
          ) {
            Text(subtext, style = MaterialTheme.typography.titleLarge)
          }
        }
      }
    }

    val repsLabel = exerciseSet.repsUnit.capitalize(Locale.current)
      .ifBlank { stringResource(id = R.string.reps_label) }
    Spacer(Modifier.weight(1f))
    Text(
      repsLabel,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier
        .heightIn(min = 30.dp)
        .align(Alignment.CenterHorizontally)
    )
    Spacer(Modifier.weight(1f))
  }
}

private enum class RepsRangeLock { NONE, LOW, HIGH }

/**
 * Reps target stepper for edit mode, extending [TargetStepper]'s open/AMRAP handling with an
 * optional rep range ("10-12"). A lock pins one end of the range - the opposite button then
 * grows or shrinks the gap to it instead of moving [reps] itself. At most one side locks at a
 * time; toggling a lock on, off, or to the other side always collapses back to an exact target
 * (repsRange reset to 0) rather than trying to walk the other end back in - shrinking a range
 * means unlocking and rebuilding it.
 */
@Composable
private fun RepsRangeStepper(
  setId: String,
  reps: Int,
  repsRange: Int,
  onChange: (reps: Int, repsRange: Int) -> Unit,
  valueStyle: TextStyle
) {
  val isSet = reps >= 0
  // Defaults to the low-end lock when restoring a persisted range with no remembered choice yet
  // - the natural reading of a stored "10-12" is "10, locked, growing up toward 12".
  var lock by rememberSaveable(setId) {
    mutableStateOf(if (repsRange > 0) RepsRangeLock.LOW else RepsRangeLock.NONE)
  }
  val displayValue = when {
    !isSet -> "—"
    repsRange > 0 -> "$reps-${reps + repsRange}"
    else -> "$reps"
  }

  fun toggleLock(side: RepsRangeLock) {
    lock = if (lock == side) RepsRangeLock.NONE else side
    onChange(reps, 0)
  }

  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      displayValue,
      style = valueStyle,
      textAlign = TextAlign.Center,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.fillMaxWidth()
    )
    // Each button is paired with the lock that governs it and the two pairs pushed to the
    // card's edges, rather than stacking +/- above the locks - that split the row width
    // between two differently-sized rows the parent Column centered independently, so the
    // locks never actually lined up under the button each one pins.
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = {
            when (lock) {
              RepsRangeLock.NONE -> onChange((reps - 1).coerceAtLeast(1), 0)
              RepsRangeLock.HIGH -> onChange(reps - 1, repsRange + 1)
              RepsRangeLock.LOW -> Unit
            }
          },
          enabled = isSet && lock != RepsRangeLock.LOW && reps > 1
        ) {
          Icon(Icons.Default.Remove, contentDescription = "decrease reps")
        }
        LockToggle(
          locked = lock == RepsRangeLock.LOW,
          enabled = isSet,
          contentDescription = "lock the low end of the reps range",
          onClick = { toggleLock(RepsRangeLock.LOW) }
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        LockToggle(
          locked = lock == RepsRangeLock.HIGH,
          enabled = isSet,
          contentDescription = "lock the high end of the reps range",
          onClick = { toggleLock(RepsRangeLock.HIGH) }
        )
        IconButton(
          onClick = {
            when (lock) {
              RepsRangeLock.NONE -> onChange((reps.takeIf { isSet } ?: 0) + 1, 0)
              RepsRangeLock.LOW -> onChange(reps, repsRange + 1)
              RepsRangeLock.HIGH -> Unit
            }
          },
          enabled = lock != RepsRangeLock.HIGH
        ) {
          Icon(Icons.Default.Add, contentDescription = "increase reps")
        }
      }
    }
  }
}

@Composable
private fun LockToggle(
  locked: Boolean,
  enabled: Boolean,
  contentDescription: String,
  onClick: () -> Unit
) {
  IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
    Icon(
      if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
      contentDescription = contentDescription,
      modifier = Modifier.size(16.dp),
      tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    )
  }
}

@Composable
@Preview(apiLevel = 36)
fun RepsDisplayPreview(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {

  val records = remember { mutableStateListOf<Record>() }
  val currentRecord =
    remember { mutableStateOf(Record(25.0, exerciseSet.reps(0), exerciseSet, Instant.now())) }
  Card(Modifier.size(170.dp)) {
    RepsDisplay(
      setWithRecord = ExerciseSetWithRecord(
        exerciseSet,
        currentRecord,
        numCompleted = 1,
        setRecords = records,
        allSets = emptyFlow()
      ),
      Repetitions(95)
    )
  }
}

@Composable
@Preview(apiLevel = 36)
fun RepsDisplayEditingPreview(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  var reps by remember { mutableStateOf(exerciseSet.reps) }
  var repsRange by remember { mutableStateOf(exerciseSet.repsRange) }
  val records = remember { mutableStateListOf<Record>() }
  val currentRecord =
    remember { mutableStateOf(Record(25.0, exerciseSet.reps(0), exerciseSet, Instant.now())) }
  // Sized to match the real portrait card rather than a 170dp square, which under-reports the
  // width available and hides overcrowding.
  Card(Modifier.size(width = 181.dp, height = RepsDisplayEditingMinHeight)) {
    RepsDisplay(
      setWithRecord = ExerciseSetWithRecord(
        exerciseSet.copy(reps = reps, repsRange = repsRange),
        currentRecord,
        numCompleted = 1,
        setRecords = records,
        allSets = emptyFlow()
      ),
      Repetitions(95),
      editing = true,
      onUpdateTargetReps = { newReps, newRepsRange ->
        reps = newReps
        repsRange = newRepsRange
      }
    )
  }
}

@Composable
@Preview(apiLevel = 36)
fun RepsDisplayEditingRangePreview(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  var reps by remember { mutableStateOf(10) }
  var repsRange by remember { mutableStateOf(2) }
  val records = remember { mutableStateListOf<Record>() }
  val currentRecord =
    remember { mutableStateOf(Record(25.0, exerciseSet.reps(0), exerciseSet, Instant.now())) }
  Card(Modifier.size(width = 181.dp, height = RepsDisplayEditingMinHeight)) {
    RepsDisplay(
      setWithRecord = ExerciseSetWithRecord(
        exerciseSet.copy(reps = reps, repsRange = repsRange),
        currentRecord,
        numCompleted = 1,
        setRecords = records,
        allSets = emptyFlow()
      ),
      Repetitions(95),
      editing = true,
      onUpdateTargetReps = { newReps, newRepsRange ->
        reps = newReps
        repsRange = newRepsRange
      }
    )
  }
}