# :ui Module

## Purpose

Presentation layer with ViewModels (business logic) and Jetpack Compose UI (view layer). Complete user interface for the fitness tracking app.

## Key Responsibilities

- ViewModels: `ExerciseViewModel`, `WorkoutViewModel`, `UserViewModel`
- Jetpack Compose UI for all screens
- Navigation and routing
- Saveable state objects for configuration changes
- Resources (strings, drawables, fonts)

## Important Files

**ViewModels:**
- `models/ExerciseViewModel.kt` - Exercise execution, complex Flow combinations, pagination
- `models/WorkoutViewModel.kt` - Workout selection, progress tracking, SavedStateHandle
- `models/UserViewModel.kt` - Authentication, feature flags

**Compose UI:**
- `compose/Top.kt` - Main navigation
- `compose/calendar/Main.kt` - Workout selection screen
- `compose/exercise/Main.kt` - Exercise execution screen
- `compose/state/Weight.kt`, `Repetitions.kt` - Saveable state with custom `Saver`
- `compose/util/Theme.kt` - Material 3 theme

## Dependencies

- `api(project(":data"))` - Domain models and repositories
- `implementation(project(":util"), project(":identity"))`
- `api(libs.androidx.paging.*)` - Exposed as api for ViewModels
- Jetpack Compose, Hilt, Firebase (types only)

## Compose Gotchas

- `Flow.collectAsState()` delegates to `produceState`, whose backing `mutableStateOf` is
  **unkeyed** — only the collector coroutine restarts when the flow instance changes. If a
  composable slot is reused across different data sources (e.g. a pager's single detail pane
  swapping which exercise it shows), the state keeps the *previous* source's last value until
  the new flow's first emission arrives. Key the state yourself
  (`remember(flow) { mutableStateOf(...) }` + `LaunchedEffect(flow) { flow.collect { ... } } }`)
  when a stale read during that gap would be visibly wrong. See `SetTrendStrip.kt`'s
  `rememberEffortSets` for the pattern.

- **Don't reach for `Popup` to escape a clip.** A `Popup` is a real `WindowManager` window, and
  adding/removing one lands on exactly the frames an animation can least afford — the start and
  the end. Before using one, check whether an ancestor already provides unclipped space: `Box`,
  `Column`, and `Row` do not clip, so only a genuinely clipping ancestor (`LazyLayout`, and
  therefore `HorizontalPager`, or an explicit `clipToBounds`/`clip`) is a real constraint.
  Hosting an overlay in the nearest unclipped ancestor gets the same reach for free.
  `PagerInstruction.kt`'s `AlternateSwapOverlay` is the worked example.

- **Derive transient animation state, don't set it from an effect.** State written in a
  `LaunchedEffect` lands a frame after the composition that triggered it, so a UI that both
  mounts an animation and hides what it replaces gets one frame showing the new content
  unanimated. Keep a "settled" value in state that only an effect advances, and compute the
  in-flight transition as a pure derivation of settled-vs-current — then both sides of the
  handoff happen in the same composition pass. See `settledSet`/`swap` in
  `PagerExerciseInstructions`.

- **M3's `ModalBottomSheet` is a real overlay, invoked conditionally — not M2's always-mounted
  wrapper.** M2's `ModalBottomSheetLayout` wrapped the whole screen and kept `sheetContent`
  composed continuously even while hidden, so expensive collection/side-effects inside it needed
  a manual `sheetState.isVisible` gate. `ModalBottomSheet` has no such gotcha: gate it yourself
  with `if (showSheet) { ModalBottomSheet(...) { ... } }` and it simply isn't in the composition
  until shown. To close it with the built-in exit animation from code (not a user swipe/scrim
  tap), await `sheetState.hide()` before flipping the `showSheet` flag —
  `scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) showSheet = false }`
  — flipping the flag directly removes the sheet with no animation. See the add-exercise and
  weight-edit sheets in `exercise/Main.kt`.

- **No Compose-native reactive API exists for raw `DisplayCutout.boundingRects`** — unlike
  `WindowInsets.displayCutout`, which is a single flattened rect per edge, Compose has no
  built-in way to observe the cutout's actual shape/position. Use
  `rememberDisplayCutoutBoundingRects()` in `compose/util/CutoutBounds.kt` (backed by
  `View.setOnApplyWindowInsetsListener`) rather than re-deriving that listener pattern per call
  site. Its `cutoutAffects(paneBounds, cutoutRects)` helper turns a cutout's bounds and a pane's
  measured `LayoutCoordinates.boundsInWindow()` into a plain on/off decision — see
  `calendar/Main.kt` and `exercise/Main.kt`, which use it to decide whether each side-by-side
  pane should consume `WindowInsets.displayCutout` at all, instead of guessing from which screen
  edge a pane's role owns. The rects/bounds coordinate-space alignment can't be unit tested —
  verify on a real cutout-configured device/emulator, not just `CutoutBoundsTest`.

## Testing

```bash
./gradlew :ui:test
```

## Used By

- `:app` - Hosts Compose UI in Activity, provides repository implementations
