package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.compose.state.Repetitions
import com.litus_animae.refitted.compose.util.NumberPicker
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.Record
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepsDisplay(
  setWithRecord: ExerciseSetWithRecord,
  reps: Repetitions
) {
  val exerciseSet = setWithRecord.exerciseSet
  Card(
    Modifier
      .fillMaxSize()
      .padding(start = 5.dp)
  ) {
    Column(
      Modifier.padding(bottom = 5.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // FIXME MAX is toFailure....
      val targetRepsTextContent = when {
        setWithRecord.reps < 0 -> "MAX"
        exerciseSet.repsUnit.isNotBlank() && exerciseSet.repsRange > 0 && !exerciseSet.isToFailure ->
          "${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange} ${exerciseSet.repsUnit}"

        exerciseSet.repsUnit.isNotBlank() && exerciseSet.repsRange > 0 && exerciseSet.isToFailure ->
          "${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange} ${exerciseSet.repsUnit} (MAX)"

        exerciseSet.repsUnit.isNotBlank() && !exerciseSet.isToFailure -> "${setWithRecord.reps} ${exerciseSet.repsUnit}"
        exerciseSet.repsUnit.isNotBlank() && exerciseSet.isToFailure -> "${setWithRecord.reps} ${exerciseSet.repsUnit} (MAX)"
        exerciseSet.repsRange > 0 && !exerciseSet.isToFailure -> "${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange}"
        exerciseSet.repsRange > 0 && exerciseSet.isToFailure -> "${setWithRecord.reps}-${setWithRecord.reps + exerciseSet.repsRange} (MAX)"
        exerciseSet.isToFailure -> "${setWithRecord.reps} (MAX)"
        else -> "${setWithRecord.reps}"
      }

      val typography = MaterialTheme.typography.h3
      val currentRepsValue by reps.value

      BoxWithConstraints(
        Modifier
          .weight(3f)
          .fillMaxWidth()
      ) {
        val pageWidth = 80.dp
        val pageCount = 101
        NumberPicker(pageCount, currentRepsValue, pageWidth, typography) {
          reps.set(it)
        }
      }
      val lineColor = contentColorFor(MaterialTheme.colors.surface)
      Divider(
        Modifier.width(70.dp),
        color = lineColor,
        thickness = 3.dp
      )
      Row(Modifier.weight(3f)) {
        Column(
          Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Top,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // FIXME when "seconds" it doesn't fit
          // TODO probably use java period for localization
          Text(targetRepsTextContent, style = typography)
        }
      }

      val repsLabel = stringResource(id = R.string.reps_label)
      Text(
        repsLabel,
        style = MaterialTheme.typography.h5,
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .weight(1f)
      )
    }
  }
}

@Composable
@Preview
fun RepsDisplayPreview(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {

  val records = remember { mutableStateListOf<Record>() }
  val currentRecord =
    remember { mutableStateOf(Record(25.0, exerciseSet.reps(0), exerciseSet, Instant.now())) }
  Box(Modifier.size(250.dp)) {
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