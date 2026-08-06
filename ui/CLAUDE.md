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

## Testing

```bash
./gradlew :ui:test
```

## Used By

- `:app` - Hosts Compose UI in Activity, provides repository implementations
