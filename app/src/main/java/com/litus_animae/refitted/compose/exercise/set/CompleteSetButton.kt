package com.litus_animae.refitted.compose.exercise.set

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import arrow.core.Option
import arrow.core.getOrElse
import com.litus_animae.refitted.R
import com.litus_animae.refitted.util.MonadUtil.optionWhen


@Composable
fun CompleteExerciseSetButton(
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
  exerciseIncomplete: Boolean,
  sets: Int,
  repsCompleted: Int,
  saveReps: Int,
  superSetStep: Int?,
  numCompleted: Int,
  isTimerRunning: Boolean
) {
  Button(
    onClick,
    modifier.fillMaxWidth(),
    enabled = exerciseIncomplete
  ) {
    val cancelRestPhrase = stringResource(id = R.string.cancel_rest)
    val exerciseCompletePhrase = stringResource(id = R.string.complete_exercise)
    val toCompletionSetPhrase = optionWhen(sets < 0) {
      if (repsCompleted < 0)
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
        superSetStep?.let{
          String.format(
            pluralStringResource(
              id = R.plurals.complete_superset_part_x,
              count = sets
            ),
            it + 1
          )
        } ?:
          String.format(
            stringResource(id = R.string.complete_set_of_workout),
            numCompleted + 1
          )
      }
    val setText =
      if (exerciseIncomplete) completeSetPhrase
      else exerciseCompletePhrase
    val buttonText = if (isTimerRunning) cancelRestPhrase else setText
    Text(buttonText, style = MaterialTheme.typography.h5)
  }
}
