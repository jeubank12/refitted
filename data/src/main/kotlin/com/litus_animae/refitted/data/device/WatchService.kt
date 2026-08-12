package com.litus_animae.refitted.data.device

import kotlinx.coroutines.flow.StateFlow

sealed interface WatchState {
  data object Unsupported : WatchState
  data object NoDevice : WatchState
  data class Idle(val deviceName: String, val appInstalled: Boolean, val appOpen: Boolean) : WatchState
  data class Active(val deviceName: String, val workout: String, val day: String) : WatchState
}

interface WatchService {
  val state: StateFlow<WatchState>
  suspend fun refresh()
  suspend fun startSession(plan: WatchPlan): Result<Unit>
  suspend fun endSession()
}
