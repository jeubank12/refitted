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
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepsDisplay(
  setWithRecord: ExerciseSetWithRecord,
  reps: Repetitions
) {
  val exerciseSet = setWithRecord.exerciseSet
  Column(
    Modifier
      .padding(bottom = 5.dp)
      .heightIn(min = RepsDisplayMinHeight),
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

    val typography = MaterialTheme.typography.h3
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
    val lineColor = contentColorFor(MaterialTheme.colors.surface)
    Divider(
      Modifier.width(70.dp),
      color = lineColor,
      thickness = 3.dp
    )
    Row {
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
          Text(subtext, style = MaterialTheme.typography.h6)
        }
      }
    }

    val repsLabel = exerciseSet.repsUnit.capitalize(Locale.current)
      .ifBlank { stringResource(id = R.string.reps_label) }
    Spacer(Modifier.weight(1f))
    Text(
      repsLabel,
      style = MaterialTheme.typography.h5,
      modifier = Modifier
        .heightIn(min = 30.dp)
        .align(Alignment.CenterHorizontally)
    )
    Spacer(Modifier.weight(1f))
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