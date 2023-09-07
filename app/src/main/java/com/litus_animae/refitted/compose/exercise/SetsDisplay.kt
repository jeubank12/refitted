package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import arrow.core.getOrElse
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.Record
import com.litus_animae.refitted.util.MonadUtil

@Composable
fun SetsDisplay(
  exerciseSet: ExerciseSet,
  numCompleted: Int,
  record: Record
) {
  Box(
    Modifier.fillMaxSize(),
  ) {
    Column(
      Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      val toCompletionSetPhrase = MonadUtil.optionWhen(exerciseSet.sets < 0) {
        val expectedReps = exerciseSet.reps(numCompleted)
        if (expectedReps < 0) {
          // This is a "complete as many as possible" set
          "${record.cumulativeReps}"
        } else {
          // This is a "complete this many reps in as many sets as you need" set
          "${expectedReps - record.cumulativeReps} / $expectedReps"
        } to "Reps Completed"
      }
      val (setDisplay, setSubtext) = toCompletionSetPhrase
        .getOrElse {
          "$numCompleted / ${exerciseSet.sets}" to "Sets Completed"
        }
      Text(setDisplay, style = MaterialTheme.typography.h4)
      Text(setSubtext, style = MaterialTheme.typography.h5)
    }
  }
}