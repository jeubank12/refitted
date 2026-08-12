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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Duration
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

// Watch heartbeats every ~10s (connectiqApp.mc) while its app is foreground - this timeout needs
// enough slack over that interval to absorb a missed beat without flickering the send button.
private val HELLO_TIMEOUT: Duration = Duration.ofSeconds(25)
private const val APP_OPEN_POLL_INTERVAL_MS = 5_000L

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
  private var lastHelloAt: Instant? = null
  private var appOpenPollerJob: Job? = null

  override suspend fun refresh() {
    awaitReady()
    try {
      val connectIQ = connection.connectIQ
      val knownDevice = connectIQ.knownDevices.orEmpty().firstOrNull()
      // ExerciseViewModel (scoped per nav destination) calls this on every init, but this
      // service is a @Singleton - unregister a prior device's listeners first, or repeated
      // navigation piles up registrations and every incoming message gets handled N times.
      unregisterDeviceListeners(connectIQ)
      device = knownDevice
      _state.value = if (knownDevice == null) {
        WatchState.NoDevice
      } else {
        connectIQ.registerForDeviceEvents(knownDevice) { _, _ -> }
        connectIQ.registerForAppEvents(knownDevice, watchApp) { _, _, message, status ->
          onMessageReceived(message, status)
        }
        startAppOpenPoller()
        WatchState.Idle(knownDevice.friendlyName, appInstalled = true, appOpen = false)
      }
    } catch (e: InvalidStateException) {
      _state.value = WatchState.NoDevice
    } catch (e: ServiceUnavailableException) {
      _state.value = WatchState.Unsupported
    }
  }

  private fun unregisterDeviceListeners(connectIQ: ConnectIQ) {
    val previousDevice = device ?: return
    runCatching { connectIQ.unregisterForDeviceEvents(previousDevice) }
    runCatching { connectIQ.unregisterForApplicationEvents(previousDevice, watchApp) }
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
    _state.value = WatchState.Idle(targetDevice.friendlyName, appInstalled = true, appOpen = true)
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

  // The SDK's send-status callback can fire more than once for a single sendMessage call (Phase
  // 0's finding that ConnectIQAdbStrategy unconditionally fires FAILURE_UNKNOWN after SUCCESS over
  // the simulator's tethered transport - confirmed here happening over real BLE too, via a crash:
  // resuming an already-resumed CancellableContinuation throws IllegalStateException and crashes
  // the whole app). Every branch must check isActive before resuming, not just the failure one.
  private suspend fun sendMessage(targetDevice: IQDevice, payload: List<Any>) {
    suspendCancellableCoroutine { continuation ->
      try {
        connection.connectIQ.sendMessage(targetDevice, watchApp, payload) { _, _, status ->
          if (continuation.isActive) {
            if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
              continuation.resume(Unit)
            } else {
              continuation.resumeWith(Result.failure(IllegalStateException("send failed: $status")))
            }
          }
        }
      } catch (e: InvalidStateException) {
        continuation.resumeWith(Result.failure(e))
      } catch (e: ServiceUnavailableException) {
        continuation.resumeWith(Result.failure(e))
      }
    }
  }

  // A message outside an active session (stale device, ended workout) is silently dropped rather
  // than crashing.
  private fun onMessageReceived(message: List<Any>, status: ConnectIQ.IQMessageStatus) {
    if (status != ConnectIQ.IQMessageStatus.SUCCESS) return
    // A watch's Communications.transmit() arrives here wrapped in an extra List layer, unlike a
    // phone's sendMessage() payload on the watch's registerForPhoneAppMessages side - confirmed
    // on-device, not documented in the SDK. Unwrap the single envelope element before decoding.
    val envelopeMessage = (message.singleOrNull() as? List<*>) ?: message
    try {
      when (val envelope = WatchProtocol.decode(envelopeMessage)) {
        is WatchProtocol.SetDone -> handleSetDone(envelope)
        is WatchProtocol.Buffer -> handleBuffer(envelope)
        is WatchProtocol.SessionEnded -> handleSessionEnded()
        is WatchProtocol.Hello -> {
          lastHelloAt = Instant.now()
          val currentState = _state.value
          if (currentState is WatchState.Idle && !currentState.appOpen) {
            _state.value = currentState.copy(appOpen = true)
          }
          log.d(TAG, "watch hello: app v${envelope.watchAppVersion}, max protocol v${envelope.maxProtocolVersion}")
        }
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
      currentSession.recordPersisted(setDone.seq)
      sendAck(currentSession.highestSeqPersisted)
    }
  }

  // The watch replays its whole unacked buffer after a dropped connection, so entries here may
  // include ones already persisted from an earlier SET_DONE that never got ACK'd back (RoomDao's
  // OnConflictStrategy.IGNORE makes a re-store a no-op) - dedup is free, nothing extra to do here.
  private fun handleBuffer(buffer: WatchProtocol.Buffer) {
    val currentSession = session ?: return
    val records = buffer.entries.mapNotNull { it.toSetRecord(currentSession) }
    if (records.isEmpty()) return
    scope.launch {
      setRecordSink.store(records)
      buffer.entries.forEach { currentSession.recordPersisted(it.seq) }
      sendAck(currentSession.highestSeqPersisted)
    }
  }

  private suspend fun sendAck(highestSeqPersisted: Int) {
    val targetDevice = device ?: return
    runCatching { sendMessage(targetDevice, WatchProtocol.encodeAck(highestSeqPersisted)) }
  }

  // The watch sends this once, right before exiting, whether the user saved or discarded from
  // ExitConfirmMenu - both outcomes end the watch-side session the same way from the phone's
  // perspective, so there's nothing further to distinguish here.
  private fun handleSessionEnded() {
    val targetDevice = device ?: return
    session = null
    connection.sessionActive = false
    _state.value = WatchState.Idle(targetDevice.friendlyName, appInstalled = true, appOpen = true)
  }

  // The watch's HELLO heartbeat (connectiqApp.mc, ~10s while foreground) is the only signal the
  // phone has for "the watch app is still open" - sendMessage's SUCCESS status only confirms Garmin
  // Connect Mobile delivered a payload to the device, not that the target IQApp received it. This
  // job ages out appOpen once heartbeats stop arriving, rather than waiting on an explicit
  // goodbye message the watch has no reliable way to send from a tearing-down onStop.
  private fun startAppOpenPoller() {
    appOpenPollerJob?.cancel()
    appOpenPollerJob = scope.launch {
      while (isActive) {
        delay(APP_OPEN_POLL_INTERVAL_MS)
        val currentState = _state.value
        if (currentState !is WatchState.Idle || !currentState.appOpen) continue
        val lastHello = lastHelloAt
        if (lastHello == null || Duration.between(lastHello, Instant.now()) > HELLO_TIMEOUT) {
          _state.value = currentState.copy(appOpen = false)
        }
      }
    }
  }
}
