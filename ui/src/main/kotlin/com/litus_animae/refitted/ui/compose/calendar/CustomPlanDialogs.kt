package com.litus_animae.refitted.ui.compose.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Name-only creation dialog for a custom plan (design 1b) - days and exercises are added
 * afterward from the calendar, so there's nothing else to ask up front.
 */
@Composable
fun NewCustomWorkoutDialog(
  onDismissRequest: () -> Unit,
  onCreate: (String) -> Unit
) {
  var name by rememberSaveable { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("New Custom Workout") },
    text = {
      Column {
        Text(
          "You're creating your own workout plan from scratch. Give it a name — you'll add days and exercises from the calendar as you go.",
          style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        TextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Plan name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
        Text("Create")
      }
    },
    dismissButton = { DialogCancelButton(onDismissRequest) }
  )
}

/**
 * Edits a custom plan: rename, or delete it outright. [errorMessage] surfaces a rejected rename
 * (e.g. name already taken) inline without dismissing the dialog, so the user can edit and retry.
 *
 * Delete sits apart from the Cancel/Rename pair and is the only error-colored control here -
 * it's destructive, and [onDelete] only opens the confirmation, it never deletes directly.
 */
@Composable
fun RenamePlanDialog(
  currentName: String,
  errorMessage: String?,
  onDismissRequest: () -> Unit,
  onRename: (newName: String) -> Unit,
  onDelete: () -> Unit
) {
  var name by rememberSaveable { mutableStateOf(currentName) }
  AlertDialog(
    onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("Rename Plan") },
    text = {
      Column {
        TextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Plan name") },
          singleLine = true,
          isError = errorMessage != null,
          modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage != null) {
          Spacer(Modifier.height(4.dp))
          Text(
            errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall
          )
        }
      }
    },
    // M3's AlertDialog has no raw "buttons" slot (M2's escape hatch for a custom button row) -
    // this 3-button row (Delete/Cancel/Rename) goes entirely in confirmButton, dismissButton
    // left unset.
    confirmButton = {
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = onDelete,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          // TODO localize
          Text("Delete")
        }
        Spacer(Modifier.weight(1f))
        DialogCancelButton(onDismissRequest)
        Spacer(Modifier.width(8.dp))
        Button(
          onClick = { onRename(name.trim()) },
          enabled = name.isNotBlank() && name.trim() != currentName
        ) {
          // TODO localize
          Text("Rename")
        }
      }
    }
  )
}

/**
 * Confirms deletion of a custom plan - deletion is irreversible (unlike resetting workout
 * completion), so this warns explicitly that all days, exercises, and history are removed, and
 * the confirm button is error-colored rather than the usual primary.
 */
@Composable
fun DeletePlanConfirmDialog(
  planName: String,
  onDismissRequest: () -> Unit,
  onConfirm: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("Delete Plan") },
    text = {
      Text("This permanently deletes \"$planName\" - all of its days, exercises, and completion history. This cannot be undone.")
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.onError
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
 * Cancel/dismiss control for the plan dialogs - low-emphasis by design, so it doesn't compete
 * with the affirmative (or destructive) action next to it.
 */
@Composable
private fun DialogCancelButton(onClick: () -> Unit) {
  TextButton(
    onClick = onClick,
    colors = ButtonDefaults.textButtonColors(
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
  ) {
    // TODO localize
    Text("Cancel")
  }
}

/**
 * Per-day edit actions (edit mode only). Delete-with-renumber and move/reorder are deferred -
 * both would renumber later days and misalign historical SetRecords keyed "day.step".
 */
@Composable
fun DayEditDialog(
  day: Int,
  isRestDay: Boolean,
  onDismissRequest: () -> Unit,
  onEditDay: () -> Unit,
  onClear: () -> Unit,
  onSetRest: (isRest: Boolean) -> Unit
) {
  // A plain Dialog+Surface instead of AlertDialog - this is really a compact action list, and
  // AlertDialog's title/text/buttons scaffold (sized for a couple of lines of prose plus a
  // button row) left a large empty gap below a short list of actions.
  Dialog(onDismissRequest = onDismissRequest) {
    Surface(shape = MaterialTheme.shapes.medium, shadowElevation = 24.dp) {
      Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
        // TODO localize
        Text(
          "Day $day",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))
        if (isRestDay) {
          DayEditAction("Remove rest day") {
            onSetRest(false)
            onDismissRequest()
          }
        } else {
          // Only way into the day screen with the add-exercise affordance enabled - see
          // Exercise(editing=).
          DayEditAction("Edit day") {
            onEditDay()
            onDismissRequest()
          }
          DayEditAction("Clear contents") {
            onClear()
            onDismissRequest()
          }
          DayEditAction("Make rest day") {
            onSetRest(true)
            onDismissRequest()
          }
        }
        Row(
          Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, end = 8.dp),
          horizontalArrangement = Arrangement.End
        ) {
          Button(onClick = onDismissRequest) {
            // TODO localize
            Text("Close")
          }
        }
      }
    }
  }
}

@Composable
private fun DayEditAction(label: String, onClick: () -> Unit) {
  Text(
    // TODO localize
    label,
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 14.dp),
    style = MaterialTheme.typography.bodyLarge
  )
}
