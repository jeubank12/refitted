# :app Module

## Purpose

Glue/orchestration layer. Android application entry points, Hilt DI configuration, and repository implementations bridging Room (local) + DynamoDB (remote).

## Key Responsibilities

- Application entry: `RefittedApplication`, `RefittedComposeActivity`
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

## Design

**Offline-First Strategy:**
- Reads from Room (local cache)
- Writes to Room immediately, network asynchronously
- Uses `AsyncPagingDataDiffer`, `RemoteMediator` for pagination
