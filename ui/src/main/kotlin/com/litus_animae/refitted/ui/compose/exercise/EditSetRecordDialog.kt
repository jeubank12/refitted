package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.litus_animae.refitted.data.models.SetRecord
import java.time.Instant

/**
 * `TextFieldValue` isn't Bundle-storable on its own, so `rememberSaveable` needs an explicit
 * saver - see EditSetRecordDialog's weight/reps fields, which select-all-on-focus and therefore
 * must carry [TextFieldValue.selection] through configuration changes alongside the text.
 */
private val TextFieldValueSaver = listSaver<TextFieldValue, Any>(
  save = { listOf(it.text, it.selection.start, it.selection.end) },
  restore = {
    TextFieldValue(
      text = it[0] as String,
      selection = TextRange(it[1] as Int, it[2] as Int)
    )
  }
)

/**
 * Edits a single logged set's weight/reps in place. The completion timestamp isn't editable -
 * it's part of SetRecord's persisted identity (see ExerciseRepository.updateSetRecord) - so
 * [record] only supplies starting values and the key carried through [onSave]/[onDelete]. Delete
 * only opens [DeleteSetRecordConfirmDialog] via [onDelete], matching CustomPlanDialogs.kt's
 * RenamePlanDialog rule that a destructive action never fires directly from the edit form.
 */
@Composable
fun EditSetRecordDialog(
  record: SetRecord,
  onDismissRequest: () -> Unit,
  onSave: (weight: Double, reps: Int) -> Unit,
  onDelete: () -> Unit
) {
  // Keyed on the record's identity (exercise + completed) - these rows come from a Paging
  // source, so if this dialog's composable slot were ever reused for a different SetRecord,
  // unkeyed state would leak the previous record's in-progress edit into the new one (see
  // ui/CLAUDE.md's Compose Gotchas).
  var weight by rememberSaveable(record.exercise, record.completed, stateSaver = TextFieldValueSaver) {
    mutableStateOf(TextFieldValue(record.weight.toString()))
  }
  var reps by rememberSaveable(record.exercise, record.completed, stateSaver = TextFieldValueSaver) {
    mutableStateOf(TextFieldValue(record.reps.toString()))
  }
  val parsedWeight = weight.text.replace(",", ".").toDoubleOrNull()
  val parsedReps = reps.text.toIntOrNull()
  val isWeightError = parsedWeight == null || parsedWeight < 0
  val isRepsError = parsedReps == null || parsedReps < 0

  AlertDialog(
    onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("Edit Set") },
    text = {
      Column {
        SelectAllOnFocusField(
          value = weight,
          onValueChange = { weight = it },
          // TODO localize
          label = "Weight",
          isError = isWeightError,
          keyboardType = KeyboardType.Decimal
        )
        Spacer(Modifier.height(8.dp))
        SelectAllOnFocusField(
          value = reps,
          onValueChange = { reps = it },
          // TODO localize
          label = "Reps",
          isError = isRepsError,
          keyboardType = KeyboardType.Number
        )
      }
    },
    buttons = {
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = onDelete,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.error)
        ) {
          // TODO localize
          Text("Delete")
        }
        Spacer(Modifier.weight(1f))
        DialogCancelButton(onDismissRequest)
        Spacer(Modifier.width(8.dp))
        Button(
          onClick = { onSave(parsedWeight ?: record.weight, parsedReps ?: record.reps) },
          enabled = !isWeightError && !isRepsError
        ) {
          // TODO localize
          Text("Save")
        }
      }
    }
  )
}

/**
 * A numeric [OutlinedTextField] that selects its entire value every time it gains focus, so
 * typing a replacement overwrites rather than requiring the user to delete the old value first.
 * Setting the selection directly from a focus callback doesn't survive: the same tap that focuses
 * the field also drives BasicTextField's own tap-to-place-cursor `onValueChange`, which fires
 * after and overwrites the selection we set. Applying it from [LaunchedEffect] instead means it
 * runs as a coroutine dispatched after that frame's composition, so it lands after the tap's own
 * update rather than racing it - and re-fires on every focus/blur cycle since it's keyed directly
 * off `isFocused`, not a manually consumed one-shot flag.
 */
@Composable
private fun SelectAllOnFocusField(
  value: TextFieldValue,
  onValueChange: (TextFieldValue) -> Unit,
  label: String,
  isError: Boolean,
  keyboardType: KeyboardType
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  LaunchedEffect(isFocused) {
    if (isFocused) onValueChange(value.copy(selection = TextRange(0, value.text.length)))
  }
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = true,
    isError = isError,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    interactionSource = interactionSource,
    modifier = Modifier.fillMaxWidth()
  )
}

/**
 * Confirms deletion of a single logged set - mirrors CustomPlanDialogs.kt's
 * DeletePlanConfirmDialog: deletion is irreversible, so it gets its own explicit step and an
 * error-colored confirm rather than the usual primary.
 */
@Composable
fun DeleteSetRecordConfirmDialog(
  onDismissRequest: () -> Unit,
  onConfirm: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("Delete Set") },
    text = {
      // TODO localize
      Text("This permanently removes this set from your history. This cannot be undone.")
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(
          backgroundColor = MaterialTheme.colors.error,
          contentColor = MaterialTheme.colors.onError
        )
      ) {
        // TODO localize
        Text("Delete")
      }
    },
    dismissButton = { DialogCancelButton(onDismissRequest) }
  )
}

/**
 * Cancel/dismiss control for the set-record dialogs - low-emphasis by design, so it doesn't
 * compete with the affirmative (or destructive) action next to it. Same shape as
 * CustomPlanDialogs.kt's private DialogCancelButton.
 */
@Composable
private fun DialogCancelButton(onClick: () -> Unit) {
  TextButton(
    onClick = onClick,
    colors = ButtonDefaults.textButtonColors(
      contentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
    )
  ) {
    // TODO localize
    Text("Cancel")
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewEditSetRecordDialog() {
  MaterialTheme {
    EditSetRecordDialog(
      record = SetRecord(
        weight = 120.0,
        reps = 8,
        workout = "TestWorkout",
        targetSet = "1.1",
        completed = Instant.now(),
        exercise = "Chest_Bench Press"
      ),
      onDismissRequest = {},
      onSave = { _, _ -> },
      onDelete = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewDeleteSetRecordConfirmDialog() {
  MaterialTheme {
    DeleteSetRecordConfirmDialog(onDismissRequest = {}, onConfirm = {})
  }
}
