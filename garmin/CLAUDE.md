# :garmin Module

## Purpose

Phone-side bridge to a Garmin Connect IQ watch app, via the Connect IQ Mobile SDK for Android.
Named after the SDK, not the transport - Garmin Connect Mobile owns the BLE radio via an AIDL
service; this module never touches `BluetoothAdapter` directly.

## Key Responsibilities

- Owns the Connect IQ SDK's process-wide `initialize`/`shutdown` lifecycle (`GarminConnection`)
- Implements `WatchService` from `:data` (`GarminWatchService`) - session start/end, connection state
- Encodes/decodes the wire protocol via `WatchProtocol` (in `:data`, no SDK dependency)
- Receives `SET_DONE` from the watch and writes it through `SetRecordSink` (in `:data`)

## Important Files

- `GarminConnection.kt` - `@Singleton`, `DefaultLifecycleObserver` hooked to
  `ProcessLifecycleOwner` in `RefittedApplication`. Initializes on first `onStart`, shuts down on
  `onStop` only when no session is active. Debug builds hard-crash on a leaked SDK binding
  (`VmPolicy.detectLeakedClosableObjects().penaltyDeath()`), so this pairing must stay exact.
- `GarminWatchService.kt` - Implements `WatchService`. Maps every `InvalidStateException` /
  `ServiceUnavailableException` at the boundary onto `WatchState.NoDevice` / `WatchState.Unsupported`
  - these must never escape into `:ui`, which has no way to name them. Registers
  `ConnectIQ.registerForAppEvents(IQDevice, IQApp, IQApplicationEventListener)` alongside device
  events once a device is known; incoming messages are decoded via `WatchProtocol.decode` and, for
  `SetDone`, resolved against the session's `WatchSessionState` (`toSetRecord`) and written through
  `SetRecordSink` on a service-owned `CoroutineScope` - this class is `@Singleton`, so it can't rely
  on a caller's scope living as long as an incoming message might arrive.

## Gotchas

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
