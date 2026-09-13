package com.litus_animae.refitted.data.device

import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

sealed interface WatchState {
  data object Unsupported : WatchState
  data object NoDevice : WatchState

  /** [lastHelloAt] is null until the watch's HELLO heartbeat has arrived at least once for this device. */
  data class Idle(
    val deviceName: String,
    val appInstalled: Boolean,
    val appOpen: Boolean,
    val lastHelloAt: Instant? = null
  ) : WatchState

  data class Active(val deviceName: String, val workout: String, val day: String) : WatchState
}

interface WatchService {
  val state: StateFlow<WatchState>

  /** Every device Connect IQ currently knows about, populated by [refresh] - not just the one [state] tracks. */
  val availableDevices: StateFlow<List<WatchDevice>>

  suspend fun refresh()

  /** Switches [state] to track the device with this [WatchDevice.id], from the last [refresh]. */
  suspend fun selectDevice(deviceId: String)
  suspend fun startSession(plan: WatchPlan): Result<Unit>
  suspend fun endSession()
}
