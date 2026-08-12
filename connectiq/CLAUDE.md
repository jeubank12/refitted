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
- `WatchProtocol.mc` - wire format encode/decode, mirrors `data/.../device/WatchProtocol.kt` exactly

## Gotchas

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

- **Module-level `const` in a `module { }` block cannot be `private`** - `monkeyc` rejects
  `private const` at module scope with a parser error, unlike inside a `class`.

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
