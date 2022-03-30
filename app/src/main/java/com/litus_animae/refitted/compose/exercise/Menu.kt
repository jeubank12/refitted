package com.litus_animae.refitted.compose.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.R
import com.litus_animae.refitted.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview

@OptIn(FlowPreview::class)
@Composable
fun RowScope.ExerciseContextMenu(instruction: ExerciseViewModel.ExerciseInstruction) {
  if (instruction.hasAlternate) {
    val (alerted, setAlerted) = remember { mutableStateOf(false) }
    if (alerted) {
      AlertDialog(onDismissRequest = { setAlerted(false) },
        title = { Text("Alternate Exercises") },
        text = { Text("Select from alternate exercises") },
        buttons = {
          val activeIndex by instruction.activeIndex.collectAsState()
          LazyColumn(Modifier.padding(bottom = 10.dp))
          {
            itemsIndexed(instruction.sets) { index, set ->
              Row(
                Modifier
                  .padding(horizontal = 5.dp)
                  .fillParentMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                val onClick = {
                  instruction.activateAlternate(index)
                  setAlerted(false)
                }
                RadioButton(
                  selected = index == activeIndex,
                  onClick = onClick
                )
                Text(set.exerciseName, Modifier.clickable(onClick = onClick))
              }
            }
          }
        })
    }
    Column(Modifier
      .clickable {
        if (instruction.alternateCount > 2) setAlerted(true)
        else instruction.activateNextAlternate()
      }
      .fillMaxHeight(),
      verticalArrangement = Arrangement.Center) {
      val altLabel = stringResource(id = R.string.alternate)
      Text(altLabel, Modifier.padding(end = 5.dp))
    }
  }
}
