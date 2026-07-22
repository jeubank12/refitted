package com.litus_animae.refitted.ui.compose.exercise.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val muscleGroups = listOf(
  "Chest", "Shoulders", "Biceps", "Triceps", "Forearms", "Core",
  "Traps", "Lats", "Lower back", "Glutes", "Hamstrings", "Quads", "Calves"
)

// There's no cross-plan exercise catalog yet (exercises today belong to one workout's admin
// content), so this list is a placeholder shown for any muscle group - selecting an entry still
// adds a real exercise to the day, it just isn't tailored to what was tapped.
private val stubExercises = listOf(
  "Barbell Bench Press", "Incline Dumbbell Press", "Weighted Dip", "Cable Fly",
  "Machine Chest Press", "Push-Up", "Decline Barbell Press", "Dumbbell Pullover"
)

/**
 * Add-exercise flow (design group D): pick a target muscle, then an exercise from the list.
 * [onExercisePicked] is called with the chosen exercise's display name.
 */
@Composable
fun AddExercisePicker(
  onExercisePicked: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedMuscle by rememberSaveable { mutableStateOf<String?>(null) }
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        // TODO localize
        title = { Text(selectedMuscle ?: "Add exercise") },
        backgroundColor = MaterialTheme.colors.primary,
        navigationIcon = {
          IconButton(onClick = { if (selectedMuscle != null) selectedMuscle = null else onClose() }) {
            if (selectedMuscle != null) Icon(Icons.AutoMirrored.Filled.ArrowBack, "back")
            // TODO localize
            else Icon(Icons.Default.Close, "close")
          }
        }
      )
    }
  ) { contentPadding ->
    val muscle = selectedMuscle
    if (muscle == null) {
      MuscleGroupPicker(Modifier.padding(contentPadding), onContinue = { selectedMuscle = it })
    } else {
      StubExerciseList(Modifier.padding(contentPadding), onPick = onExercisePicked)
    }
  }
}

@Composable
private fun MuscleGroupPicker(modifier: Modifier = Modifier, onContinue: (String) -> Unit) {
  var selected by rememberSaveable { mutableStateOf(muscleGroups.first()) }
  Column(
    modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
      // TODO localize
      Text("Tap the muscle group to target", style = MaterialTheme.typography.body2)
    }
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      muscleGroups.forEach { muscle ->
        MuscleChip(muscle, selected = muscle == selected, onClick = { selected = muscle })
      }
    }
    Spacer(Modifier.weight(1f))
    Button(
      onClick = { onContinue(selected) },
      modifier = Modifier.fillMaxWidth()
    ) {
      // TODO localize
      Text("Continue — $selected")
    }
  }
}

@Composable
private fun MuscleChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
    contentColor = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
    border = if (selected) null else BorderStroke(
      1.dp,
      MaterialTheme.colors.onSurface.copy(alpha = 0.23f)
    ),
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Text(label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.body2)
  }
}

@Composable
private fun StubExerciseList(modifier: Modifier = Modifier, onPick: (String) -> Unit) {
  LazyColumn(modifier.fillMaxSize()) {
    items(stubExercises) { exercise ->
      Row(
        Modifier
          .fillMaxWidth()
          .clickable { onPick(exercise) }
          .padding(start = 10.dp, end = 6.dp, top = 15.dp, bottom = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(exercise, style = MaterialTheme.typography.button)
        Icon(Icons.Default.Add, "add $exercise", tint = MaterialTheme.colors.primary)
      }
      Divider()
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewAddExercisePicker() {
  MaterialTheme {
    AddExercisePicker(onExercisePicked = {}, onClose = {})
  }
}
