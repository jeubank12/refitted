package com.litus_animae.refitted.garmin

import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.litus_animae.refitted.data.device.SetRecordSink
import com.litus_animae.refitted.data.device.WatchPlan
import com.litus_animae.refitted.data.device.WatchProtocol
import com.litus_animae.refitted.data.device.WatchService
import com.litus_animae.refitted.data.device.WatchSessionState
import com.litus_animae.refitted.data.device.WatchState
import com.litus_animae.refitted.data.device.toSetRecord
import com.litus_animae.refitted.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Pairing key with `connectiq/manifest.xml`'s `<iq:application id="...">`, generated once by the
 * Connect IQ project scaffold - both sides ship it hardcoded (Open Question 5, resolved).
 */
private const val REFITTED_WATCH_APP_ID = "7fb7b276-65e1-47df-a7d2-0d31553e0b4d"
private const val TAG = "GarminWatchService"

@Singleton
class GarminWatchService @Inject constructor(
  private val connection: GarminConnection,
  private val setRecordSink: SetRecordSink,
  private val log: LogUtil
) : WatchService {

  private val watchApp = IQApp(REFITTED_WATCH_APP_ID)
  private val _state = MutableStateFlow<WatchState>(WatchState.NoDevice)
  override val state: StateFlow<WatchState> = _state.asStateFlow()

  // Owned here rather than reusing a caller's scope - incoming SET_DONE messages can arrive at
  // any point in this @Singleton's lifetime, not just while a ViewModel is collecting state.
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var device: IQDevice? = null
  private var session: WatchSessionState? = null

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
        connectIQ.registerForAppEvents(knownDevice, watchApp) { _, _, message, status ->
          onMessageReceived(message, status)
        }
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
      session = WatchSessionState(workout = plan.workout, startInstant = Instant.now(), plan = plan)
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

  // BUFFER/HELLO are Phase 3/4 concerns (offline replay) - only SET_DONE and SESSION_ENDED are
  // handled here. A message outside an active session (stale device, ended workout) is silently
  // dropped rather than crashing.
  private fun onMessageReceived(message: List<Any>, status: ConnectIQ.IQMessageStatus) {
    if (status != ConnectIQ.IQMessageStatus.SUCCESS) return
    // A watch's Communications.transmit() arrives here wrapped in an extra List layer, unlike a
    // phone's sendMessage() payload on the watch's registerForPhoneAppMessages side - confirmed
    // on-device, not documented in the SDK. Unwrap the single envelope element before decoding.
    val envelopeMessage = (message.singleOrNull() as? List<*>) ?: message
    try {
      when (val envelope = WatchProtocol.decode(envelopeMessage)) {
        is WatchProtocol.SetDone -> handleSetDone(envelope)
        is WatchProtocol.SessionEnded -> handleSessionEnded()
        is WatchProtocol.UnsupportedVersion ->
          log.w(TAG, "watch sent unsupported protocol version ${envelope.receivedVersion}")
        else -> {}
      }
    } catch (e: Exception) {
      log.e(TAG, "failed to decode watch message: $envelopeMessage", e)
    }
  }

  private fun handleSetDone(setDone: WatchProtocol.SetDone) {
    val currentSession = session ?: return
    val record = setDone.toSetRecord(currentSession) ?: return
    scope.launch {
      setRecordSink.store(listOf(record))
    }
  }

  // The watch sends this once, right before exiting, whether the user saved or discarded from
  // ExitConfirmMenu - both outcomes end the watch-side session the same way from the phone's
  // perspective, so there's nothing further to distinguish here.
  private fun handleSessionEnded() {
    val targetDevice = device ?: return
    session = null
    connection.sessionActive = false
    _state.value = WatchState.Idle(targetDevice.friendlyName, appInstalled = true)
  }
}
