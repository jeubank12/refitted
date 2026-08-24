# :garmin Module

## Purpose

Phone-side bridge to a Garmin Connect IQ watch app, via the Connect IQ Mobile SDK for Android.
Named after the SDK, not the transport - Garmin Connect Mobile owns the BLE radio via an AIDL
service; this module never touches `BluetoothAdapter` directly.

## Key Responsibilities

- Owns the Connect IQ SDK's process-wide `initialize`/`shutdown` lifecycle (`GarminConnection`)
- Implements `WatchService` from `:data` (`GarminWatchService`) - session start/end, connection state
- Encodes/decodes the wire protocol via `WatchProtocol` (in `:data`, no SDK dependency)
- Receives `SET_DONE`/`BUFFER` from the watch, writes them through `SetRecordSink` (in `:data`),
  and ACKs back the session's `highestSeqPersisted` so the watch can trim its offline queue
- Tracks whether the watch app is actually open (`WatchState.Idle.appOpen`) via its `HELLO`
  heartbeat, since `ConnectIQ.sendMessage`'s `SUCCESS` status only confirms delivery to the
  *device*, not that the target `IQApp` is foregrounded and listening - see Gotchas

## Important Files

- `GarminConnection.kt` - `@Singleton`, `DefaultLifecycleObserver` hooked to
  `ProcessLifecycleOwner` in `RefittedApplication`. Initializes on first `onStart`, shuts down on
  `onStop` only when no session is active, and skips re-initializing on a later `onStart` while
  already ready (`sdkReady` can still be `true` from a prior session-active `onStop` that skipped
  shutdown). Debug builds hard-crash on a leaked SDK binding
  (`VmPolicy.detectLeakedClosableObjects().penaltyDeath()`), so this pairing must stay exact.
- `GarminWatchService.kt` - Implements `WatchService`. Maps every `InvalidStateException` /
  `ServiceUnavailableException` at the boundary onto `WatchState.NoDevice` / `WatchState.Unsupported`
  - these must never escape into `:ui`, which has no way to name them. `refresh()` populates
  `availableDevices` from every `ConnectIQ.knownDevices` entry (not just the first - see Gotchas)
  and only falls back to auto-selecting the first known device into `state` when nothing is
  currently selected - a device already selected, whether by an earlier `refresh()` or by an
  explicit `selectDevice(id)`, survives repeat `refresh()` calls (one per `ExerciseViewModel`
  init, plus one per watch-sync dialog open) rather than being silently reset back to device 0.
  `selectDevice(id)` is the explicit path `:ui`'s device-picker dialog uses to switch `state` to a
  different known device; both it and `refresh()` share
  `registerDeviceListeners`, which unregisters the previously selected device's listeners before
  registering `ConnectIQ.registerForAppEvents(IQDevice, IQApp, IQApplicationEventListener)`
  alongside device events for the new one - this service is `@Singleton` but `refresh()` runs once
  per `ExerciseViewModel` init (scoped per nav destination), so skipping the unregister would pile
  up listeners on repeated navigation. Incoming messages are decoded via `WatchProtocol.decode` and, for
  `SetDone`, resolved against the session's `WatchSessionState` (`toSetRecord`) and written through
  `SetRecordSink` on a service-owned `CoroutineScope` - this class is `@Singleton`, so it can't rely
  on a caller's scope living as long as an incoming message might arrive. `SessionEnded` (sent once
  by the watch right before it exits, from `ExitConfirmMenu.mc`'s Save or Discard) resets `_state`
  back to `WatchState.Idle` and clears `sessionActive`/`session` - this is the only path back to
  Idle from Active, so a dropped or unhandled `SessionEnded` leaves the phone's watch button stuck
  showing a checkmark until the app restarts.

## Gotchas

- **`ConnectIQ.knownDevices` returns every paired device, not just the active one.** Early on,
  `GarminWatchService.refresh()` took `.firstOrNull()` and discarded the rest, so a second paired
  watch was simply invisible with no way to see it existed - the phone's "send to watch" icon
  looked broken with no indication why. `refresh()` now maps the full list into
  `WatchDevice`/`availableDevices` for `:ui` to render as a picker, while `device` (the one `state`
  and `startSession` actually target) is still chosen explicitly - either `refresh()`'s
  first-known-device default or a later `selectDevice(id)` call.

- **`sendMessage`'s `IQMessageStatus.SUCCESS` is not an app-level ack.** It only means Garmin
  Connect Mobile accepted the payload for BLE delivery to the *device* - it says nothing about
  whether the target `IQApp` is open on the watch and actually received it. There's no passive SDK
  query for "is this app currently running" either: `ConnectIQ.getApplicationInfo` only reports
  install status, and `ConnectIQ.openApplication` actively tries to launch/foreground the app on
  the watch (and can show the wearer a prompt) as a side effect of checking, so it's unsuitable for
  silent polling. Instead, `GarminWatchService` treats the watch's `HELLO` message
  (`connectiqApp.mc` sends one on `onStart` and then repeats it on a ~10s foreground heartbeat) as
  the actual open/closed signal: `lastHelloAt` is stamped on each `HELLO`, and a poller job on the
  service's `scope` ages `WatchState.Idle.appOpen` back to `false` if no heartbeat has arrived
  within `HELLO_TIMEOUT`. There is no explicit watch->phone "goodbye" message on close - a reliable
  transmit from a tearing-down `onStop` isn't guaranteed, so staleness timeout is the only signal,
  which means `appOpen` can lag reality by up to `HELLO_TIMEOUT` after the watch app actually
  closes.

- **A watch's `Communications.transmit()` arrives wrapped in an extra `List` layer.** Confirmed
  on-device (crash log: `message[0] as Number` threw `ClassCastException: ArrayList cannot be cast
  to Number`), not documented anywhere in the SDK. `IQApplicationEventListener.onMessageReceived`'s
  `message` is `[actualEnvelope]`, not `actualEnvelope` directly - unlike the phone's `sendMessage`
  payload, which the watch receives unwrapped via `registerForPhoneAppMessages`. Unwrap with
  `(message.singleOrNull() as? List<*>) ?: message` before calling `WatchProtocol.decode` (see
  `GarminWatchService.onMessageReceived`). If a future message type needs this listener too, reuse
  that unwrap rather than assuming the raw `message` is the envelope.

## Dependencies

- `api(project(":data"))`, `api(project(":util"))` - no `:room`, no `:dynamo`
- `implementation(libs.garmin.connectiq)` - Connect IQ Mobile SDK (`ciq-companion-app-sdk`)
- No `AndroidManifest.xml` - the SDK's AAR declares its own `<queries>` and services; verified by
  unpacking the AAR, not assumed. Do not add one unless a real conflict shows up in the merged
  manifest.

## Testing

```bash
./gradlew :garmin:test
```

Development without hardware: the SDK supports `IQConnectType.TETHERED` against the Connect IQ
simulator over ADB. Note the simulator's tethered transport is unreliable for watch-initiated
`transmit` (known SDK bugs, confirmed via Garmin's forums) - trust real hardware for that
direction; phone-initiated `sendMessage` (used here for `PLAN`/`END`) is fine against the simulator.

## Used By

- `:app` - `WatchServiceModule` binds `GarminWatchService` to `WatchService`; `RefittedApplication`
  registers `GarminConnection` with `ProcessLifecycleOwner`.
