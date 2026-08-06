package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import com.litus_animae.refitted.data.models.WorkoutPlan
import kotlinx.coroutines.FlowPreview

/**
 * With a single alternate, tapping swaps straight to it — a binary toggle doesn't need a picker.
 * With more than one, tapping opens the dialog to choose among all of them.
 */
@OptIn(FlowPreview::class)
@Composable
fun AlternateChip(
  instruction: ExerciseViewModel.ExerciseInstruction,
  workoutPlan: WorkoutPlan?,
  onAlternateChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = instruction.hasAlternate,
    modifier = modifier,
    enter = fadeIn() + expandHorizontally(),
    exit = fadeOut() + shrinkHorizontally()
  ) {
    var showPicker by remember { mutableStateOf(false) }
    val activeIndexFlow = remember(instruction, workoutPlan?.globalAlternate) {
      instruction.activeIndex(workoutPlan?.globalAlternate)
    }
    val activeIndex by activeIndexFlow.collectAsState(0)
    val label = if (instruction.alternateCount > 2) {
      // TODO localize
      "Alternates"
    } else {
      val nextIndex = (activeIndex + 1) % instruction.alternateCount
      workoutPlan?.globalAlternateLabels?.getOrNull(nextIndex)
        ?: instruction.sets.getOrElse(nextIndex) { instruction.sets.head }.exerciseName
    }

    Row(
      Modifier
        .clip(RoundedCornerShape(4.dp))
        .clickable {
          if (instruction.alternateCount > 2) showPicker = true
          else onAlternateChange(instruction.activateNextAlternate())
        }
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        Icons.Default.SwapHoriz,
        // TODO localize
        contentDescription = stringResource(id = R.string.switch_to_alternate),
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
      )
      Text(
        label,
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp)
      )
    }

    if (showPicker) {
      AlternatePickerDialog(instruction, workoutPlan, onDismissRequest = { showPicker = false }) {
        showPicker = false
        onAlternateChange(it)
      }
    }
  }
}

@OptIn(FlowPreview::class)
@Composable
private fun AlternatePickerDialog(
  instruction: ExerciseViewModel.ExerciseInstruction,
  workoutPlan: WorkoutPlan?,
  onDismissRequest: () -> Unit,
  onPick: (Int) -> Unit
) {
  AlertDialog(onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("Alternate Exercises") },
    text = { Text("Select from alternate exercises") },
    buttons = {
      val activeIndexFlow = remember(instruction, workoutPlan?.globalAlternate) {
        instruction.activeIndex(workoutPlan?.globalAlternate)
      }
      val activeIndex by activeIndexFlow.collectAsState(0)
      LazyColumn(Modifier.padding(bottom = 10.dp)) {
        itemsIndexed(instruction.sets) { index, set ->
          Row(
            Modifier
              .padding(horizontal = 5.dp)
              .fillParentMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val onClick: () -> Unit = {
              instruction.activateAlternate(index)
              onPick(index)
            }
            RadioButton(selected = index == activeIndex, onClick = onClick)
            Text(
              workoutPlan?.globalAlternateLabels?.getOrNull(index) ?: set.exerciseName,
              Modifier.clickable(onClick = onClick)
            )
          }
        }
      }
    })
}
