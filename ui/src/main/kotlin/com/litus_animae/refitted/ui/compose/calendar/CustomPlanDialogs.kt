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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
          style = MaterialTheme.typography.body2
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
            color = MaterialTheme.colors.error,
            style = MaterialTheme.typography.caption
          )
        }
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
 * Cancel/dismiss control for the plan dialogs - low-emphasis by design, so it doesn't compete
 * with the affirmative (or destructive) action next to it.
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

/**
 * Copy-day dialog (design 1e, minus the per-exercise preview list, Move day, and the
 * append/overwrite choice - copy is always non-destructive and always appends a new day). Lets
 * the user pick which existing day to copy from.
 */
@Composable
fun CopyDayDialog(
  totalDays: Int,
  onDismissRequest: () -> Unit,
  onCopy: (fromDay: Int) -> Unit
) {
  var fromDay by rememberSaveable { mutableIntStateOf(1.coerceAtMost(totalDays.coerceAtLeast(1))) }
  val newDayNumber = totalDays + 1
  AlertDialog(
    onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("Copy a Day") },
    text = {
      Column {
        Text(
          "Adds Day $newDayNumber with the same exercises as the day you pick below. Set and rep targets come from the sets you completed.",
          style = MaterialTheme.typography.body2
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { fromDay = (fromDay - 1).coerceAtLeast(1) },
            enabled = fromDay > 1
          ) { Icon(Icons.Default.Remove, "previous day") }
          Text("Day $fromDay", style = MaterialTheme.typography.subtitle1)
          IconButton(
            onClick = { fromDay = (fromDay + 1).coerceAtMost(totalDays) },
            enabled = fromDay < totalDays
          ) { Icon(Icons.Default.Add, "next day") }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onCopy(fromDay) }) {
        Text("Copy")
      }
    },
    dismissButton = { DialogCancelButton(onDismissRequest) }
  )
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
    Surface(shape = MaterialTheme.shapes.medium, elevation = 24.dp) {
      Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
        // TODO localize
        Text(
          "Day $day",
          style = MaterialTheme.typography.h6,
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
    style = MaterialTheme.typography.body1
  )
}
