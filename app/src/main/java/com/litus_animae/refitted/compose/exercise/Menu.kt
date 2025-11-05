package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.ExerciseViewModel
import com.litus_animae.refitted.models.WorkoutPlan
import kotlinx.coroutines.FlowPreview

// suppressed to prevent wrapping this Composable in a column
@Suppress("UnusedReceiverParameter")
@OptIn(FlowPreview::class)
@Composable
fun RowScope.ExerciseContextMenu(
  instruction: ExerciseViewModel.ExerciseInstruction,
  workoutPlan: WorkoutPlan?,
  onAlternateChange: (Int) -> Unit,
  activeIndex: Int,
  model: ExerciseViewModel = viewModel()
) {
  if (instruction.hasAlternate) {
    val (alerted, setAlerted) = remember { mutableStateOf(false) }
    if (alerted) {
      AlertDialog(
        onDismissRequest = { setAlerted(false) },
        title = { Text("Alternate Exercises") },
        text = { Text("Select from alternate exercises") },
        buttons = {
          LazyColumn(Modifier.padding(bottom = 10.dp)) {
            itemsIndexed(instruction.sets) { index, set ->
              Row(
                Modifier
                  .padding(horizontal = 5.dp)
                  .fillParentMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                val onClick: () -> Unit = {
                  model.onAlternateSelected(instruction.sets.head.primaryStep, index)
                  onAlternateChange(index)
                  setAlerted(false)
                }
                RadioButton(
                  selected = index == activeIndex,
                  onClick = onClick
                )
                Text(
                  workoutPlan?.globalAlternateLabels?.getOrNull(index) ?: set.exerciseName,
                  Modifier.clickable(onClick = onClick)
                )
              }
            }
          }
        })
    }
    Column(
      Modifier
        .clickable {
          if (instruction.alternateCount > 2) setAlerted(true)
          else {
            val updatedIndex = (activeIndex + 1) % instruction.alternateCount
            model.onAlternateSelected(instruction.sets.head.primaryStep, updatedIndex)
            onAlternateChange(updatedIndex)
          }
        }
        .fillMaxHeight(),
      verticalArrangement = Arrangement.Center) {
      val altLabel = stringResource(id = R.string.alternate)
      Text(altLabel, Modifier.padding(end = 5.dp))
    }
  }
}
