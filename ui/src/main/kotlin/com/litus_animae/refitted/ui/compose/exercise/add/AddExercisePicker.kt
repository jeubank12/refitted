package com.litus_animae.refitted.ui.compose.exercise.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AppBarDefaults
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
import androidx.compose.ui.graphics.Shape
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
 * Target-muscle screen (design 1i): body diagram + chips, both driving the same selection.
 * A separate nav destination from [ExercisePickerList] so system/gesture back steps one
 * screen at a time instead of leaving the whole add-exercise flow.
 */
@Composable
fun MuscleGroupPickerScreen(
  onContinue: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selected by rememberSaveable { mutableStateOf(muscleGroups.first()) }
  Scaffold(
    contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    modifier = modifier,
    topBar = {
      TopAppBar(
        // TODO localize
        title = { Text("Add exercise") },
        windowInsets = AppBarDefaults.topAppBarWindowInsets.union(
          WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        ),
        backgroundColor = MaterialTheme.colors.primary,
        navigationIcon = {
          // TODO localize
          IconButton(onClick = onClose) { Icon(Icons.Default.Close, "close") }
        }
      )
    }
  ) { contentPadding ->
    Column(modifier.padding(contentPadding).fillMaxSize()) {
      Column(
        Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(16.dp)
      ) {
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
          // TODO localize
          Text("Tap the muscle group to target", style = MaterialTheme.typography.body2)
        }
        Spacer(Modifier.height(14.dp))
        BodyDiagram(selected = selected, onSelect = { selected = it }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          muscleGroups.forEach { muscle ->
            MuscleChip(muscle, selected = muscle == selected, onClick = { selected = muscle })
          }
        }
      }
      Button(
        onClick = { onContinue(selected) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        // TODO localize
        Text("Continue — $selected")
      }
    }
  }
}

/**
 * Exercise-list screen (design 1j). [onPick] is called with the chosen exercise's display name.
 */
@Composable
fun ExercisePickerList(
  muscle: String,
  onPick: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(muscle) },
        windowInsets = AppBarDefaults.topAppBarWindowInsets.union(
          WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        ),
        backgroundColor = MaterialTheme.colors.primary,
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
        }
      )
    }
  ) { contentPadding ->
    LazyColumn(Modifier.padding(contentPadding).fillMaxSize()) {
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

// Front/back body diagram (design 1i) - a stand-in anatomy figure, not tied to real muscle
// imagery. Each tappable region shares selection state with the chips below it; positions are
// lifted straight from the design mockup's 150x330 canvas (px treated as dp).
private data class BodyPart(
  val muscle: String?,
  val x: Int,
  val y: Int,
  val w: Int,
  val h: Int,
  val shape: Shape
)

private val roundedPart = RoundedCornerShape(40)

private val frontBodyParts = listOf(
  BodyPart(null, 57, 0, 36, 36, CircleShape),
  BodyPart(null, 68, 35, 14, 10, RoundedCornerShape(2.dp)),
  BodyPart("Shoulders", 29, 45, 26, 16, roundedPart),
  BodyPart("Shoulders", 95, 45, 26, 16, roundedPart),
  BodyPart("Chest", 45, 47, 60, 36, RoundedCornerShape(10.dp)),
  BodyPart("Biceps", 26, 64, 15, 46, roundedPart),
  BodyPart("Biceps", 109, 64, 15, 46, roundedPart),
  BodyPart("Core", 49, 87, 52, 54, RoundedCornerShape(10.dp)),
  BodyPart("Forearms", 24, 114, 12, 44, roundedPart),
  BodyPart("Forearms", 114, 114, 12, 44, roundedPart),
  BodyPart(null, 47, 145, 56, 22, roundedPart),
  BodyPart("Quads", 47, 171, 24, 78, RoundedCornerShape(12.dp)),
  BodyPart("Quads", 79, 171, 24, 78, RoundedCornerShape(12.dp)),
  BodyPart("Calves", 50, 255, 18, 62, roundedPart),
  BodyPart("Calves", 82, 255, 18, 62, roundedPart)
)

private val backBodyParts = listOf(
  BodyPart(null, 57, 0, 36, 36, CircleShape),
  BodyPart(null, 68, 35, 14, 10, RoundedCornerShape(2.dp)),
  BodyPart("Traps", 51, 42, 48, 18, roundedPart),
  BodyPart("Shoulders", 29, 48, 20, 14, roundedPart),
  BodyPart("Shoulders", 101, 48, 20, 14, roundedPart),
  BodyPart("Lats", 45, 63, 60, 52, RoundedCornerShape(10.dp)),
  BodyPart("Triceps", 26, 64, 15, 46, roundedPart),
  BodyPart("Triceps", 109, 64, 15, 46, roundedPart),
  BodyPart("Lower back", 55, 118, 40, 24, roundedPart),
  BodyPart("Glutes", 47, 145, 56, 26, RoundedCornerShape(12.dp)),
  BodyPart("Hamstrings", 47, 175, 24, 72, RoundedCornerShape(12.dp)),
  BodyPart("Hamstrings", 79, 175, 24, 72, RoundedCornerShape(12.dp)),
  BodyPart("Calves", 50, 252, 18, 62, roundedPart),
  BodyPart("Calves", 82, 252, 18, 62, roundedPart)
)

@Composable
private fun BodyDiagram(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      BodyFigure(frontBodyParts, selected, onSelect)
      Spacer(Modifier.height(8.dp))
      // TODO localize
      Text("FRONT", style = MaterialTheme.typography.overline)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      BodyFigure(backBodyParts, selected, onSelect)
      Spacer(Modifier.height(8.dp))
      // TODO localize
      Text("BACK", style = MaterialTheme.typography.overline)
    }
  }
}

@Composable
private fun BodyFigure(parts: List<BodyPart>, selected: String, onSelect: (String) -> Unit) {
  Box(Modifier.size(150.dp, 330.dp)) {
    parts.forEach { part ->
      val isSelected = part.muscle == selected
      Box(
        Modifier
          .offset(x = part.x.dp, y = part.y.dp)
          .size(part.w.dp, part.h.dp)
          .let { base ->
            if (part.muscle != null) {
              base.clickable(onClickLabel = part.muscle, onClick = { onSelect(part.muscle) })
            } else base
          }
          .background(
            if (isSelected) MaterialTheme.colors.primary
            else MaterialTheme.colors.onSurface.copy(alpha = 0.14f),
            part.shape
          )
          .let { base ->
            if (isSelected) {
              base.border(2.dp, MaterialTheme.colors.primary.copy(alpha = 0.4f), part.shape)
            } else base
          }
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewMuscleGroupPicker() {
  MaterialTheme {
    MuscleGroupPickerScreen(onContinue = {}, onClose = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewExercisePickerList() {
  MaterialTheme {
    ExercisePickerList(muscle = "Chest", onPick = {}, onBack = {})
  }
}
