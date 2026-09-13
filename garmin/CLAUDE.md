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
  different known device; it only reassigns `device`/`state` once `registerDeviceListeners`
  actually succeeds, wrapped in the same `InvalidStateException`/`ServiceUnavailableException`
  handling as `refresh()` - a mid-switch failure resets to `NoDevice`/`Unsupported` rather than
  leaving `device` pointed at a target whose listeners never registered. Both it and `refresh()`
  null out `device` on that failure, and both share `registerDeviceListeners`, which is atomic
  w.r.t. its own two SDK calls (see Gotchas) since neither caller's cleanup can reach a listener
  registration that isn't reachable through `device`. It unregisters the previously selected
  device's listeners before registering
  `ConnectIQ.registerForAppEvents(IQDevice, IQApp, IQApplicationEventListener)`
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

- **Only one Android app may hold a live `registerForAppEvents` registration for a given
  watch-app id at a time - documented, not inferred.** Garmin's Mobile SDK for Android docs
  ("Core Topics" -> "Mobile SDK for Android" -> "Receiving Messages",
  `developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/` - the page is a
  client-rendered SPA, `WebFetch`/`curl` both return an empty shell, so verifying this meant
  having the page's text pasted in rather than fetching it) state plainly: "A companion app may
  register to receive messages from multiple apps across many devices. However, **multiple
  companion apps cannot be registered to receive messages from the same ConnectIQ application.
  The SDK will override any previous registrations with each call to
  `registerForAppEvents()`.**" `REFITTED_WATCH_APP_ID` is one hardcoded UUID shared by every
  Android build variant of this app (debug/minifiedDebug/release, all installable side by side on
  one phone) - installing more than one and letting each call `registerForAppEvents` for it means
  whichever variant registered *last* silently wins, and every other variant's listener is
  orphaned with **zero error on either side**: the watch's `Communications.transmit()` still
  reports `onComplete()` (that only confirms delivery to Garmin Connect Mobile, not to a specific
  registered listener - see the `sendMessage` gotcha below for the mirror-image case), and the
  losing app's `onMessageReceived` callback simply never fires. This was the root cause of a
  multi-week intermittent "watch says HELLO sent, phone never shows connected" investigation -
  confirmed by testing a freshly-registered debug variant while a previously-registered release
  build's listener silently went stale. Fixed here by only ever letting `release` register at all:
  `:app`'s `WatchServiceModule` is split per build type (`src/release/kotlin` binds the real
  `GarminWatchService`; `src/watchSyncDisabled/kotlin`, shared by `debug` and `minifiedDebug` via
  `app/build.gradle`'s `sourceSets`, binds a no-op `DisabledWatchService` that reports
  `WatchState.Unsupported` and never touches `registerForAppEvents`) - so at most one installed
  variant can ever hold the registration. Don't add a debug-only "also enable watch sync for
  testing" toggle without re-reading this: any second variant registering, even transiently, can
  silently steal or lose the slot from whichever is currently live. A separate, official
  Garmin-acknowledged bug
  (`forums.garmin.com/developer/connect-iq/i/bug-reports/gcm-5-27-3-android-accepts-communications-transmit-messages-from-watch-app-but-never-delivers-them-to-the-companion-app`)
  describes the same silent-non-delivery symptom occurring even with a single, correctly
  registered companion app on GCM 5.27.3+ - treat the two as separate, compounding possibilities,
  not alternatives; ruling out the multi-registration issue does not rule out this one.

- **`refresh()`/`selectDevice()` share `deviceMutex` because both read-modify-write `device` and
  `knownIQDevices` on `Dispatchers.IO`, a real multi-threaded pool.** Before device selection
  existed, `refresh()` was the only mutator and ran once per `ExerciseViewModel` init, so two
  calls landing concurrently was unlikely. `WatchSyncDialog` opening now fires its own `refresh()`
  that can race the one from `ExerciseViewModel.init`, and a device row tap fires `selectDevice()`
  that can race either - without the lock, two interleaved calls could each register/unregister
  listeners against a stale read of `device`, leaking or double-registering them, and leave
  `device`/`_state` pointed at whichever call happened to finish last. `awaitReady()` deliberately
  sits *outside* the lock (each caller waits for SDK readiness independently, so a slow/never-ready
  SDK can't starve one caller behind another that got the lock first while waiting).

- **`registerDeviceListeners`'s two SDK calls must stay atomic, or a partial failure leaks a
  listener registration for the `@Singleton`'s lifetime.** `registerForDeviceEvents` and
  `registerForAppEvents` are two separate calls; if the first succeeds but the second throws
  (`InvalidStateException`/`ServiceUnavailableException` - plausible if Connect IQ drops mid-call),
  the caller (`refresh()` or `selectDevice()`) nulls out `device` in its own catch block (see
  device/state consistency above). `unregisterDeviceListeners` reads `device` to know what to
  unregister, so once it's `null` there's no way back to the registration that *did* succeed -
  it's orphaned forever. `registerDeviceListeners` unregisters its own first call before
  rethrowing if the second one throws, so it never hands a caller a state where the device is
  half-registered - don't add a third SDK call here without extending that same rollback.

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
  closes. `lastHelloAt` is also threaded through to `WatchState.Idle` itself (not just the private
  field) so `:ui` can render "no response for Ns" instead of a flat "waiting on app" once contact
  is lost - every site that reassigns `device` to a *different* physical device (the `refresh()`
  branch past the early-return, and `selectDevice()`) resets it to `null` first, since a stale
  timestamp from the previous device would otherwise look like a fresh heartbeat from one that's
  never actually said hello.

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
