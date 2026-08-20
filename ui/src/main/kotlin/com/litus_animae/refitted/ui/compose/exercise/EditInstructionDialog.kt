package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Edit-mode dialog for a custom exercise's per-day instructions. [description] (the shared,
 * per-workout `Exercise.description`) is shown read-only for context - it's edited nowhere in the
 * app yet - while [initialNote] (the per-set `ExerciseSet.note`) is the only free-text field the
 * user can change here.
 */
@Composable
fun EditInstructionDialog(
  exerciseName: String,
  description: String?,
  initialNote: String,
  onDismissRequest: () -> Unit,
  onSave: (String) -> Unit
) {
  var note by rememberSaveable(initialNote) { mutableStateOf(initialNote) }
  AlertDialog(
    onDismissRequest = onDismissRequest,
    title = { Text(exerciseName) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (!description.isNullOrBlank()) {
          Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(Modifier.height(16.dp))
        }
        // TODO localize
        Text("Your notes", style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          minLines = 3,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      // TODO localize
      Button(onClick = { onSave(note) }) { Text("Save") }
    },
    dismissButton = {
      // TODO localize
      Button(onClick = onDismissRequest) { Text("Cancel") }
    }
  )
}

@Preview(showBackground = true)
@Composable
private fun PreviewEditInstructionDialog() {
  MaterialTheme {
    EditInstructionDialog(
      exerciseName = "Barbell Bench Press",
      description = "Lower the bar to your chest with control, then press back up.",
      initialNote = "Use the safety bars set to chest height",
      onDismissRequest = {},
      onSave = {}
    )
  }
}
