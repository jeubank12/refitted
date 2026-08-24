package com.litus_animae.refitted.data.device

enum class WatchDeviceStatus { CONNECTED, NOT_CONNECTED, NOT_PAIRED, UNKNOWN }

/**
 * One Connect IQ-known device, independent of whether it's the one currently selected for
 * [WatchService.state]. [id] is the SDK's device identifier, stable across refreshes.
 */
data class WatchDevice(
  val id: String,
  val name: String,
  val status: WatchDeviceStatus
)
