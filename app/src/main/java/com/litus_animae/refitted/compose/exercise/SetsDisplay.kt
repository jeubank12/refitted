package com.litus_animae.refitted.compose.exercise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.Record

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
      val expectedReps = exerciseSet.reps(numCompleted)
      val progressText = when {
        exerciseSet.sets < 0 && expectedReps < 0 -> "${record.cumulativeReps}"
        exerciseSet.sets < 0 -> "${expectedReps - record.cumulativeReps}"
        numCompleted >= exerciseSet.sets -> "$numCompleted"
        else -> "${numCompleted + 1}"
      }
      val showProgressGoal = when {
        exerciseSet.sets < 0 -> expectedReps >= 0
        else -> numCompleted < exerciseSet.sets
      }
      val progressGoal =
        remember { mutableIntStateOf(if (exerciseSet.sets < 0) expectedReps else exerciseSet.sets) }
      LaunchedEffect(exerciseSet) {
        if (exerciseSet.sets < 0) {
          if (expectedReps >= 0) progressGoal.intValue = expectedReps
        } else if (numCompleted < exerciseSet.sets) {
          progressGoal.intValue = exerciseSet.sets
        }
      }
      val showAsCompleted = when {
        exerciseSet.sets < 0 -> true
        else -> numCompleted >= exerciseSet.sets
      }

      AnimatedVisibility(
        !showAsCompleted,
        enter = expandVertically(),
        exit = shrinkVertically()
      ) {
        Text("Set", style = MaterialTheme.typography.h5)
      }
      Row {
        Text(progressText, style = MaterialTheme.typography.h4)
        AnimatedVisibility(
          showProgressGoal,
          enter = expandHorizontally(expandFrom = Alignment.Start),
          exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
          Text(" / ${progressGoal.intValue}", style = MaterialTheme.typography.h4)
        }
      }
      AnimatedVisibility(
        showAsCompleted,
        enter = expandVertically(expandFrom = Alignment.Top),
        exit = shrinkVertically(shrinkTowards = Alignment.Top)
      ) {
        Row {
          AnimatedVisibility(
            exerciseSet.sets < 0,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            Text("Reps", style = MaterialTheme.typography.h5)
          }
          AnimatedVisibility(
            exerciseSet.sets >= 0,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            Text("Sets", style = MaterialTheme.typography.h5)
          }
          Text(" Completed", style = MaterialTheme.typography.h5)
        }
      }
    }
  }
}