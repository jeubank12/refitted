package com.litus_animae.refitted.ui.compose.exercise.set

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import arrow.core.Option
import arrow.core.getOrElse
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.util.tertiaryLight
import com.litus_animae.refitted.ui.util.MonadUtil.optionWhen


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
  isTimerRunning: Boolean,
  watchSessionActive: Boolean = false
) {
  val background = if (isSystemInDarkTheme()) tertiaryLight else MaterialTheme.colorScheme.primary
  Button(
    onClick,
    modifier.fillMaxWidth(),
    enabled = exerciseIncomplete && !watchSessionActive,
    colors = ButtonColors(background, contentColorFor(background), background.copy(alpha = 0.1f), contentColorFor(background.copy(0.1f)))
  ) {
    val cancelRestPhrase = String.format(stringResource(id = R.string.cancel_rest), numCompleted + 1)
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
    val buttonText = when {
      !exerciseIncomplete -> exerciseCompletePhrase
      isTimerRunning -> cancelRestPhrase
      else -> completeSetPhrase
    }
    Text(buttonText, style = MaterialTheme.typography.headlineSmall)
  }
}
