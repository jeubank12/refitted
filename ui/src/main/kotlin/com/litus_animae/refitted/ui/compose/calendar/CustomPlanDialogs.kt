package com.litus_animae.refitted.ui.compose.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
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
          "Name your workout. You will add days and exercises from the calendar as you go.",
          style = MaterialTheme.typography.body2
        )
        Spacer(Modifier.height(16.dp))
        TextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Name") },
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
    dismissButton = {
      Button(onClick = onDismissRequest) { Text("Cancel") }
    }
  )
}

/**
 * Copy-day dialog (design 1e, minus the per-exercise preview list and Move day). [onCopy]
 * receives null to append a new day, or an existing day number to overwrite.
 */
@Composable
fun CopyDayDialog(
  fromDay: Int,
  totalDays: Int,
  onDismissRequest: () -> Unit,
  onCopy: (toDay: Int?) -> Unit
) {
  var appendAsNewDay by rememberSaveable { mutableStateOf(true) }
  var chosenDay by rememberSaveable { mutableIntStateOf(1.coerceAtMost(totalDays.coerceAtLeast(1))) }
  val newDayNumber = totalDays + 1
  AlertDialog(
    onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("Copy Day $fromDay") },
    text = {
      Column {
        Text(
          "Creates a new day with the same exercises. Set and rep targets come from the sets you completed.",
          style = MaterialTheme.typography.body2
        )
        Spacer(Modifier.height(16.dp))
        Row(
          Modifier
            .fillMaxWidth()
            .clickable { appendAsNewDay = true },
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(selected = appendAsNewDay, onClick = { appendAsNewDay = true })
          Text("Add as new day — Day $newDayNumber")
        }
        Row(
          Modifier
            .fillMaxWidth()
            .clickable { appendAsNewDay = false },
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(selected = !appendAsNewDay, onClick = { appendAsNewDay = false })
          Text("Choose a day…")
        }
        if (!appendAsNewDay && totalDays > 0) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { chosenDay = (chosenDay - 1).coerceAtLeast(1) },
              enabled = chosenDay > 1
            ) { Icon(Icons.Default.Remove, "previous day") }
            Text("Day $chosenDay", style = MaterialTheme.typography.subtitle1)
            IconButton(
              onClick = { chosenDay = (chosenDay + 1).coerceAtMost(totalDays) },
              enabled = chosenDay < totalDays
            ) { Icon(Icons.Default.Add, "next day") }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onCopy(if (appendAsNewDay) null else chosenDay) }) {
        Text("Copy")
      }
    },
    dismissButton = {
      Button(onClick = onDismissRequest) { Text("Cancel") }
    }
  )
}
