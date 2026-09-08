# :app Module

## Purpose

Glue/orchestration layer. Android application entry points, Hilt DI configuration, and repository implementations bridging Room (local) + DynamoDB (remote).

## Key Responsibilities

- Application entry: `RefittedApplication`, `RefittedComposeActivity`
- Firebase App Check provider install (`appcheck/AppCheckInitializer`, called from `RefittedApplication.onCreate()`)
- Hilt DI modules providing all implementations
- Repository implementations: `RoomCacheExerciseRepository`, `RoomCacheWorkoutPlanRepository` (offline-first)
- Paging coordination: `ExerciseSetPager`, `WorkoutPlanRemoteMediator`

## Important Files

- `RefittedApplication.kt` - `@HiltAndroidApp` entry point
- `RefittedComposeActivity.kt` - `@AndroidEntryPoint` hosting Compose UI
- `data/room/RoomCacheExerciseRepository.kt` - Implements `ExerciseRepository` with offline-first caching
- `data/room/RoomCacheWorkoutPlanRepository.kt` - Implements `WorkoutPlanRepository` with `RemoteMediator`
- `data/room/ExerciseSetPager.kt` - Custom `PagingSource` for exercises
- `data/room/WorkoutPlanRemoteMediator.kt` - Network sync coordinator
- `module/*Module.kt` - Hilt DI bindings

## Dependencies

- All feature modules: `:ui`, `:room`, `:dynamo`, `:identity`
- Activity Compose, Hilt, Firebase (app-level)
- Room compiler (KSP)

## Testing

```bash
./gradlew :app:test
```

## Firebase App Check

`AppCheckInitializer` is **variant-specific** (no copy in `src/main`): `src/debug/kotlin`
installs the Debug provider, and `src/appCheckProd/kotlin` installs Play Integrity — wired
into both `release` and `minifiedDebug` via a `sourceSets` block in `app/build.gradle`
(`src/release/` is gitignored, so the shared set can't live there). `src/main` carries no
`firebase-appcheck*` dependency — `firebase-appcheck-debug` is `debugImplementation` only,
so the attestation-bypass artifact never reaches the release classpath.

Debug builds log a token to logcat (`DebugAppCheckProvider`) on first run; register it in the
Firebase console (App Check → Manage debug tokens) for that build to be trusted once
enforcement is on. App Check only covers Firebase-native calls (Auth, Remote Config,
Analytics) — the DynamoDB/Cognito path in `:dynamo` cannot carry a token.

## Design

**Offline-First Strategy:**
- Reads from Room (local cache)
- Writes to Room immediately, network asynchronously
- Uses `AsyncPagingDataDiffer`, `RemoteMediator` for pagination
