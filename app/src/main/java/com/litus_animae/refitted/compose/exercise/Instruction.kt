package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.compose.util.Theme
import com.litus_animae.refitted.models.ExerciseSet
import kotlinx.coroutines.Dispatchers

@Preview(showBackground = true)
@Composable
fun PreviewExerciseInstructions(@PreviewParameter(ExampleExerciseProvider::class) exerciseSet: ExerciseSet) {
  MaterialTheme(Theme.darkColors) {
    Column {
      this.ExerciseInstructions(
        exerciseSet = exerciseSet
      )
    }
  }
}

@Composable
fun RowScope.ExerciseInstructions(
  exerciseSet: ExerciseSet?,
  modifier: Modifier = Modifier
) {
  Column(modifier) {
    ExerciseInstructions(exerciseSet)
  }
}

@Composable
fun ColumnScope.ExerciseInstructions(
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