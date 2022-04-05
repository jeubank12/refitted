package com.litus_animae.refitted.compose.exercise.input

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.litus_animae.refitted.compose.state.Repetitions
import com.litus_animae.refitted.compose.util.ConstrainedButton
import com.litus_animae.refitted.compose.util.ConstrainedText
import com.litus_animae.refitted.compose.util.ConstrainedTextBox
import com.litus_animae.refitted.compose.util.Theme

@Composable
fun RepetitionsButtons(reps: Repetitions) {
  val currentReps by reps.value
  BoxWithConstraints {
    // TODO if available size is too small.....
    val maxButtonWidth = maxWidth - 10.dp
    val maxButtonHeight = (maxHeight - 30.dp) * 2 / 7
    val availableSize = min(maxButtonWidth, maxButtonHeight)
    val buttonSize = min(availableSize, 50.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
        RepetitionButton(1, reps::plus, buttonSize)
      }
      SideEffect {
        Log.d("RepetitionsButtons", "Reps is $currentReps")
      }
      Row(
        Modifier
          .size(width = buttonSize, height = buttonSize * 3 / 2)
          .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Column {
          val repsLabel = stringResource(id = com.litus_animae.refitted.R.string.reps_label)
          ConstrainedText(repsLabel)
          ConstrainedTextBox("99") { _, fontSize ->
            ValueTextField(
              displayedText = currentReps.toString(),
              onEdit = { it.replace("[^\\d]".toRegex(), "") },
              onEditComplete = {
                val updatedReps = it.toIntOrNull() ?: 0
                reps.set(updatedReps)
                updatedReps.toString()
              },
              verticalPadding = 5.dp,
              fontSize = fontSize
            )
          }
        }
      }
      Row(Modifier.padding(bottom = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
        RepetitionButton(-1, reps::plus, buttonSize)
      }
    }
  }
}

@Composable
private fun RepetitionButton(repetitions: Int, onClick: (Int) -> Unit, size: Dp) {
  ConstrainedButton(
    String.format("%+d", repetitions),
    Modifier.size(size),
    onClick = { onClick(repetitions) },
    contentPadding = PaddingValues(5.dp)
  )
}

@Composable
@Preview(heightDp = 300)
fun PreviewRepetitionButtons() {
  MaterialTheme(Theme.lightColors) {
    val reps = remember { Repetitions(8) }
    Column(Modifier.fillMaxSize()) {
      RepetitionsButtons(reps)
    }
  }
}

@Composable
@Preview(heightDp = 150, widthDp = 75)
fun PreviewSmallRepetitionButtons() {
  MaterialTheme(Theme.lightColors) {
    val reps = remember { Repetitions(8) }
    Column(Modifier.fillMaxSize()) {
      RepetitionsButtons(reps)
    }
  }
}