package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.state.ExerciseSetWithRecord
import com.litus_animae.refitted.compose.state.Repetitions
import com.litus_animae.refitted.models.ExerciseSet

@Composable
fun RepsDisplay(
  setWithRecord: ExerciseSetWithRecord,
  exerciseSet: ExerciseSet,
  reps: Repetitions
) {
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

      Row(Modifier.weight(3f)) {
        Column(
          Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Bottom,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          val currentRepsValue by reps.value
          Text("$currentRepsValue", style = MaterialTheme.typography.h3)
        }
      }
      val lineColor = contentColorFor(MaterialTheme.colors.surface)
        Divider(Modifier.padding(horizontal = 40.dp),
          color = lineColor,
          thickness = 3.dp)
      Row(Modifier.weight(3f)) {
        Column(
          Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Top,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(targetRepsTextContent, style = MaterialTheme.typography.h3)
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