package com.litus_animae.refitted.garmin

import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.litus_animae.refitted.data.device.WatchPlan
import com.litus_animae.refitted.data.device.WatchProtocol
import com.litus_animae.refitted.data.device.WatchService
import com.litus_animae.refitted.data.device.WatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Pairing key with `connectiq/manifest.xml`'s `<iq:application id="...">`, generated once by the
 * Connect IQ project scaffold - both sides ship it hardcoded (Open Question 5, resolved).
 */
private const val REFITTED_WATCH_APP_ID = "7fb7b276-65e1-47df-a7d2-0d31553e0b4d"

@Singleton
class GarminWatchService @Inject constructor(
  private val connection: GarminConnection
) : WatchService {

  private val watchApp = IQApp(REFITTED_WATCH_APP_ID)
  private val _state = MutableStateFlow<WatchState>(WatchState.NoDevice)
  override val state: StateFlow<WatchState> = _state.asStateFlow()

  private var device: IQDevice? = null

  override suspend fun refresh() {
    awaitReady()
    try {
      val connectIQ = connection.connectIQ
      val knownDevice = connectIQ.knownDevices.orEmpty().firstOrNull()
      device = knownDevice
      _state.value = if (knownDevice == null) {
        WatchState.NoDevice
      } else {
        connectIQ.registerForDeviceEvents(knownDevice) { _, _ -> }
        WatchState.Idle(knownDevice.friendlyName, appInstalled = true)
      }
    } catch (e: InvalidStateException) {
      _state.value = WatchState.NoDevice
    } catch (e: ServiceUnavailableException) {
      _state.value = WatchState.Unsupported
    }
  }

  override suspend fun startSession(plan: WatchPlan): Result<Unit> {
    val targetDevice = device ?: return Result.failure(IllegalStateException("no watch device registered"))
    val payload = WatchProtocol.encodePlan(plan.workout, plan.day, plan.exercises)

    return runCatching {
      sendMessage(targetDevice, payload)
      connection.sessionActive = true
      _state.value = WatchState.Active(targetDevice.friendlyName, plan.workout, plan.day)
    }
  }

  override suspend fun endSession() {
    val targetDevice = device ?: return
    runCatching { sendMessage(targetDevice, WatchProtocol.encodeEnd()) }
    connection.sessionActive = false
    _state.value = WatchState.Idle(targetDevice.friendlyName, appInstalled = true)
  }

  // GarminConnection.initialize() is async - onSdkReady() can land well after this service is
  // first asked to refresh (e.g. a cold app start where the exercise screen mounts immediately).
  // Wait for it rather than hitting InvalidStateException and settling on NoDevice for good.
  private suspend fun awaitReady() = suspendCancellableCoroutine<Unit> { continuation ->
    connection.whenReady {
      if (continuation.isActive) {
        continuation.resume(Unit)
      }
    }
  }

  private suspend fun sendMessage(targetDevice: IQDevice, payload: List<Any>) {
    suspendCancellableCoroutine { continuation ->
      try {
        connection.connectIQ.sendMessage(targetDevice, watchApp, payload) { _, _, status ->
          if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
            continuation.resume(Unit)
          } else if (continuation.isActive) {
            continuation.resumeWith(Result.failure(IllegalStateException("send failed: $status")))
          }
        }
      } catch (e: InvalidStateException) {
        continuation.resumeWith(Result.failure(e))
      } catch (e: ServiceUnavailableException) {
        continuation.resumeWith(Result.failure(e))
      }
    }
  }
}
