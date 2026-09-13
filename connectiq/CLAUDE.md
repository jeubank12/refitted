# connectiq/ — Garmin Connect IQ Watch App

## Purpose

Monkey C watch app that pairs with the phone's `:garmin` module over the Connect IQ Mobile SDK's
message link. Deliberately **outside the Gradle build** (`settings.gradle` does not include it) -
different toolchain, different compiler, no CI job (see Open Question 2 in `PLAN-garmin.md`).

Target hardware: Forerunner 2xx/9xx (CIQ 4/5, 5-button, no touchscreen, MB-scale watchApp memory).
See `PLAN-garmin.md` at the repo root for the full architecture, wire protocol, and phasing.

## Key Files

- `connectiqApp.mc` - app entry point; owns `activeSession` for the crash-safety net in `onStop`
- `connectiqView.mc`/`connectiqDelegate.mc` - idle screen, shown before a `PLAN` arrives
- `ExerciseListMenu.mc` - pre-workout overview (`Menu2`); selecting an exercise starts the
  `ActivityRecording` session and pushes `ActiveWorkoutView`
- `ActiveWorkout.mc` - the in-workout screen and its `InputDelegate` (button handling - see Gotchas)
- `SetAdjustPicker.mc` - the reps/weight adjust `Picker` shown before a set counts as complete
- `ExitConfirmMenu.mc` - Save/Discard `Menu2`, shown on exit mid-workout
- `DiagnosticsView.mc` - HELLO pairing diagnostics (attempt/success/error counts, time since last
  attempt/result), reachable from the idle screen's menu (`onMenu` -> `MainMenu` -> "Diagnostics").
  `DiagnosticsDelegate.onSelect` forces an immediate HELLO resend rather than waiting for the next
  `HELLO_INTERVAL_MS` tick; `onNextPage`/`onPreviousPage` (up/down buttons) scroll the content by
  one line on the rare device/content combination where it doesn't fit - see the round-bezel and
  TextWrap gotchas below
- `TextWrap.mc` - shared word-wrap helper (no layout `<label>` or SDK API wraps text on its own);
  used by both `connectiqView.mc`'s idle prompt and `ActiveWorkout.mc`'s exercise name
- `WatchProtocol.mc` - wire format encode/decode, mirrors `data/.../device/WatchProtocol.kt` exactly

## Gotchas

- **On a round device (`screenShape == SCREEN_SHAPE_ROUND`, true for both this app's targets), the
  usable width at a given row shrinks to 0 as that row approaches y=0 or y=height - it's a circle,
  not a rectangle - so text anchored near either edge gets clipped by the bezel even though it's
  well within `Dc`'s rectangular coordinate bounds.** A row drawn at literally `dc.getHeight() -
  lineHeight` (the obvious way to pin something to "the bottom") is almost always inside that
  clipped band once the row is more than a couple of characters wide - this is what made
  `DiagnosticsView`'s footer instructions read as "falls off the bottom of the screen" on real
  hardware, not an actual vertical overflow. Fix by inset, not by moving text to y=0: pick the
  widest string the screen will draw, and solve the circle-chord equation for how much vertical
  margin makes that width fit (`radius - sqrt(radius² - (width/2)²)`) - see
  `DiagnosticsView.onUpdate`'s `verticalMargin` for a worked example. Skip the inset entirely on a
  rectangular `screenShape` - it doesn't need it and margin math for a shape it isn't would just
  waste vertical space.

- **`TextWrap.wrapText` only wraps horizontally - it has no idea how tall the screen is, so a
  manually-drawn screen with variable-length or unbounded content (an error count, a device name,
  anything that can grow) can still, separately from the round-bezel margin above, produce more
  wrapped lines than fit in the vertical space that margin leaves.** There's no SDK-provided
  scrollable text view for manually-drawn content, so a screen with genuinely unbounded content
  needs its own vertical clamp/scroll on top of `TextWrap`'s horizontal one. `DiagnosticsView.mc` is
  the reference implementation: it clips the content region with `Dc.setClip` above the (inset)
  footer line, and `DiagnosticsDelegate`'s `onNextPage`/`onPreviousPage` (up/down buttons) adjust a
  line-based scroll offset and call `WatchUi.requestUpdate()` - note that's the *module-level*
  `WatchUi.requestUpdate()`, not a method on `View` (an easy guess to get wrong, since
  `View.onUpdate` reads like the counterpart).

- **Physical button → `KEY_*` mapping is per-device, and the SDK's `WatchUi.KEY_*` enum existing
  does not mean a given device delivers it.** `WatchUi.BehaviorDelegate` collapses buttons into
  named behaviors (`onSelect`, `onBack`, `onNextPage`, `onPreviousPage`, `onMenu`) - to tell two
  physical buttons apart (e.g. distinguishing Start/Stop from a Lap button) you need a raw
  `WatchUi.InputDelegate.onKey(keyEvent)` override, but that only sees whatever `KeyEvent.getKey()`
  the device actually emits, which is **not** every constant in the `WatchUi.KEY_*` table.

  The authoritative source for what a given device emits, per key, is *not* SDK API docs but each
  device's **`simulator.json`**, installed by SDK Manager at
  `<ConnectIQ data dir>/Devices/<deviceId>/simulator.json` (on Windows,
  `%APPDATA%\Garmin\ConnectIQ\Devices\<deviceId>\simulator.json`). Its `"keys"` array lists every
  physical button the device model defines, each with an `"id"` and the `BehaviorDelegate`
  `"behavior"` it triggers - e.g. `{"id": "enter", "behavior": "onSelect"}`.

  Confirmed against this file for **both `fr945` and `fr265s`** (this app's current targets): each
  defines exactly five keys - `enter` (`onSelect`), `up` (`previousPage`), `menu` (`onMenu`, a hold
  on the `up` button), `down` (`nextPage`), `esc` (`onBack`) - and **no `start` or `lap` key at
  all**. So on these devices, the physical Start/Stop button always delivers `KEY_ENTER` (never
  `KEY_START`), and the physical BACK/LAP button always delivers `KEY_ESC` (never `KEY_LAP`) -
  confirmed first on real hardware, then confirmed authoritatively against this file. `ActiveWorkout.mc`'s
  `onKey` checks both the "expected" constant and the one these devices actually send, so it degrades
  gracefully if a future device does have a true Lap key.

  **Before wiring up a new device or assuming a button behaves a certain way, check that device's
  `simulator.json` `"keys"` array first** - don't assume `KEY_START`/`KEY_LAP` (or any other
  `WatchUi.KEY_*` constant) is reachable just because it's documented as existing somewhere in the
  SDK.

- **`WatchUi.NumberPicker` is deprecated; there is no built-in `NumberFactory`.** The `Picker` docs'
  `new NumberFactory();` example is illustrative pseudocode, not a real SDK class - the SDK's own
  `Picker` sample defines its own `NumberFactory`. `SetAdjustPicker.mc`'s `IntegerPickerFactory`
  follows that same pattern. `Picker`'s `:pattern` option takes an array of `PickerFactory`/`Drawable`
  entries and `PickerDelegate.onAccept` returns one value per entry - so multiple fields (here, reps
  and weight) belong on one `Picker` screen, not chained separate `Picker`s.

- **Nothing at module scope (`const` or `function`) inside a `module { }` block can be `private`** -
  `monkeyc` rejects `private const`/`private function` at module scope with a parser error
  ("extraneous input 'private'"), unlike inside a `class`. `PendingSetBuffer.mc`'s `load`/`save`
  helpers are unmarked (not `private`) for exactly this reason - they just aren't part of the
  module's intended external surface.

- **`method(:symbolName)` needs an instance (`self`) to bind to - it doesn't work for a bare
  function defined directly inside a `module { }` block.** Referencing a module-level function as a
  first-class `Method` value from outside that module hits a "Cannot find symbol ':method' on type
  'self'" compile error. Keep formatting/callback logic that needs a `Method` reference on a class
  instance instead (see `SetAdjustPicker.mc`'s `IntegerPickerFactory`, which takes a format string +
  divisor rather than a `Method` callback).

- **A Connect IQ app exits by emptying its view stack, not via an explicit `System.exit()` call.**
  `connectiqApp.onPhoneMessage` uses `WatchUi.switchToView` to make `ExerciseListMenu` the stack's
  base (replacing the idle screen), and `ExerciseListMenuDelegate.onSelect` then `pushView`s
  `ActiveWorkoutView` on top of that. `ExitConfirmMenuDelegate.onSelect` (`ExitConfirmMenu.mc`) has
  to `popView` exactly three times on Save/Discard - the confirm menu, the active-workout screen,
  and `ExerciseListMenu` itself - to actually return to the watch face; stopping after two just
  lands back on the exercise list.

- **The Connect IQ SDK has no way to write a native FIT `set_mesg` (message type 225) the way
  Garmin's own Strength Training app does for reps/weight per set.** `ActivityRecording.Session`
  only exposes `addLap()` (writes `lap_mesg`) and `createField()` for `FitContributor` developer
  fields, whose `:mesgType` option is hard-limited to `MESG_TYPE_SESSION` (18), `MESG_TYPE_LAP`
  (19), or `MESG_TYPE_RECORD` (20) - confirmed against the local SDK docs
  (`Toybox.ActivityRecording.Session`, `Toybox.FitContributor`), there is no `MESG_TYPE_SET` and no
  other entry point for a custom native message type. `ActiveWorkout.mc`'s `session.addLap()` per
  completed set is the closest available primitive and is already in use - don't re-investigate
  this expecting a "real" set to be reachable via public API.

## Building Locally

No CI job exists for this directory. Verify changes compile before asking for a sideload:

```bash
java -Xms1g -Dfile.encoding=UTF-8 -Dapple.awt.UIElement=true \
  -jar "<sdk-root>/bin/monkeybrains.jar" \
  -o connectiq/bin/refitted_connectiq_fr945.prg -f connectiq/monkey.jungle -y developer_key -d fr945_sim -w
```

`monkeyc` on `PATH` may not exist - invoke `bin/monkeybrains.jar` directly with `java`. `-d` is the
target device id with `_sim` appended (build both `fr945_sim` and `fr265s_sim` when changing shared
code) - suffix `-o`'s output filename with the device id too (`..._fr945.prg`, `..._fr265s.prg`) so
the two builds don't overwrite each other and stay easy to tell apart in `connectiq/bin/`.
`-y developer_key` is the repo-root signing key (untracked). `-w` shows compiler warnings -
keep it on. `<sdk-root>` is wherever SDK Manager installed the active SDK
(`%APPDATA%\Garmin\ConnectIQ\Sdks\<version>` on Windows) - see `current-sdk.cfg` one level up from
`Sdks/` if more than one version is installed.

A `BUILD SUCCESSFUL` line (pre-existing warnings about the missing `<iq:languages>` and launcher
icon scaling are fine to ignore) means the source compiled cleanly.

**Verify unfamiliar Monkey C APIs against the local SDK docs (`<sdk-root>/doc/`) and, ideally, an
actual SDK sample (`<sdk-root>/samples/`) before writing code that uses them** - don't assume a
Kotlin/Java-shaped API surface exists. Guessing has cost multiple rounds of compile/on-device
errors in this file tree already (missing `String.split()`, wrong `Menu2` constructor shapes, the
`NumberPicker`/`method()` gotchas above).

## Simulator Limitations

The Connect IQ Simulator's tethered/ADB transport (`IQConnectType.TETHERED`) is unreliable for
watch-initiated `Communications.transmit` - known SDK bugs, confirmed via Garmin's forums, not a
bug in this app's code. Phone-initiated `sendMessage` (`PLAN`/`END`) is fine against the simulator;
trust real hardware for anything watch → phone (`SET_DONE`, `SESSION_ENDED`, `HELLO`, `BUFFER`).
