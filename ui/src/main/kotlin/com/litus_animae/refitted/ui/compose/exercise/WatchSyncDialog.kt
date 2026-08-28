package com.litus_animae.refitted.ui.compose.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litus_animae.refitted.data.device.WatchDevice
import com.litus_animae.refitted.data.device.WatchDeviceStatus
import com.litus_animae.refitted.data.device.WatchExercise
import com.litus_animae.refitted.data.device.WatchPlan
import com.litus_animae.refitted.data.device.WatchState
import com.litus_animae.refitted.ui.models.ExerciseViewModel
import kotlinx.coroutines.FlowPreview

/**
 * Replaces the old single-tap "send to watch" icon action: picking a device and reviewing what's
 * about to be sent are now explicit steps in one dialog, rather than one opaque tap that either
 * silently no-oped (disabled icon, no explanation) or fired the whole plan immediately. Device
 * pick and the exercise-list summary share this one screen (no separate confirm step between
 * them) - selecting a device just reveals the summary below it; only the final Send is a
 * distinct action.
 */
@OptIn(FlowPreview::class)
@Composable
fun WatchSyncDialog(
  model: ExerciseViewModel,
  globalAlternate: Int?,
  onDismissRequest: () -> Unit
) {
  val devices by model.watchDevices.collectAsStateWithLifecycle()
  val watchState by model.watchState.collectAsStateWithLifecycle()
  var selectedDeviceId by remember { mutableStateOf<String?>(null) }
  var plan by remember { mutableStateOf<WatchPlan?>(null) }

  LaunchedEffect(Unit) {
    model.refreshWatchState()
    plan = model.previewWatchPlan(globalAlternate)
  }

  // GarminWatchService.refresh() already auto-selects the first known device into watchState
  // before this dialog ever opens (see garmin/CLAUDE.md) - without this, a device that's already
  // connected shows no radio selected and the summary/Send stay hidden until a redundant tap on
  // the row that's already the active target.
  LaunchedEffect(devices, watchState) {
    if (selectedDeviceId != null) return@LaunchedEffect
    val activeDeviceName = when (val currentState = watchState) {
      is WatchState.Idle -> currentState.deviceName
      is WatchState.Active -> currentState.deviceName
      else -> null
    } ?: return@LaunchedEffect
    selectedDeviceId = devices.firstOrNull { it.name == activeDeviceName }?.id
  }

  // A selectWatchDevice failure regresses watchState to NoDevice/Unsupported (GarminWatchService's
  // own error handling) without clearing selectedDeviceId - without this, the picked row keeps
  // rendering "connected, waiting on app" via deviceStatusLabel(isSelected = true) instead of
  // reflecting that the selection actually failed.
  LaunchedEffect(watchState) {
    if (watchState is WatchState.NoDevice || watchState is WatchState.Unsupported) {
      selectedDeviceId = null
    }
  }

  val appOpen = (watchState as? WatchState.Idle)?.appOpen == true
  val sessionActive = watchState is WatchState.Active
  val canSend = selectedDeviceId != null && appOpen && plan != null && !sessionActive

  AlertDialog(
    onDismissRequest = onDismissRequest,
    // TODO localize
    title = { Text("Send to Watch") },
    text = {
      Column {
        when {
          watchState is WatchState.Unsupported ->
            Text("Watch sync isn't supported on this device.")
          sessionActive ->
            Text("A workout is already running on the watch. End it there before sending a new plan.")
          devices.isEmpty() ->
            Text("No paired Garmin devices found. Pair one in Garmin Connect Mobile, then try again.")
          else -> {
            // TODO localize
            Text("Device", style = MaterialTheme.typography.labelLarge)
            devices.forEach { device ->
              DeviceRow(
                device = device,
                isSelected = device.id == selectedDeviceId,
                appOpen = appOpen,
                onSelect = {
                  selectedDeviceId = device.id
                  model.selectWatchDevice(device.id)
                }
              )
            }
          }
        }

        if (selectedDeviceId != null && !sessionActive) {
          Spacer(Modifier.height(12.dp))
          HorizontalDivider()
          Spacer(Modifier.height(12.dp))
          // TODO localize
          Text("Exercises", style = MaterialTheme.typography.labelLarge)
          val currentPlan = plan
          if (currentPlan == null) {
            CircularProgressIndicator(Modifier.padding(16.dp))
          } else {
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
              items(currentPlan.exercises.indices.toList(), key = { currentPlan.ids[it] }) { index ->
                WatchExerciseRow(currentPlan.exercises[index])
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { model.sendPlanToWatch(globalAlternate); onDismissRequest() }, enabled = canSend) {
        // TODO localize
        Text("Send")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismissRequest) {
        // TODO localize
        Text("Cancel")
      }
    }
  )
}

@Composable
private fun DeviceRow(
  device: WatchDevice,
  isSelected: Boolean,
  appOpen: Boolean,
  onSelect: () -> Unit
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelect)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(selected = isSelected, onClick = onSelect)
    Column {
      Text(device.name, style = MaterialTheme.typography.bodyLarge)
      Text(
        deviceStatusLabel(device.status, isSelected, appOpen),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun WatchExerciseRow(exercise: WatchExercise) {
  Column(Modifier.padding(vertical = 6.dp)) {
    Text(exercise.name, style = MaterialTheme.typography.bodyMedium)
    val setsLabel = if (exercise.sets < 0) "open" else "${exercise.sets}"
    // TODO localize
    Text(
      "$setsLabel x ${exercise.reps}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

// TODO localize
private fun deviceStatusLabel(status: WatchDeviceStatus, isSelected: Boolean, appOpen: Boolean): String {
  if (isSelected) {
    return if (appOpen) "connected, app open" else "connected, waiting on app"
  }
  return when (status) {
    WatchDeviceStatus.CONNECTED -> "connected"
    WatchDeviceStatus.NOT_CONNECTED -> "not connected"
    WatchDeviceStatus.NOT_PAIRED -> "not paired"
    WatchDeviceStatus.UNKNOWN -> "status unknown"
  }
}
