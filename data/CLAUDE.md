# :data Module

## Purpose

Pure Kotlin domain layer defining business models and repository interfaces with no Android or implementation dependencies.

## Key Responsibilities

- Domain models: `Exercise`, `ExerciseSet`, `ExerciseRecord`, `Record`, `WorkoutPlan`, `SavedState`
- Repository interfaces: `ExerciseRepository`, `WorkoutPlanRepository`, `SavedStateRepository`
- Business logic for step parsing, superset detection

## Important Files

- `models/Exercise.kt` - Exercise definitions
- `models/ExerciseSet.kt` - Exercise sets with step parsing logic (computed properties: `isSuperSet`, `primaryStep`, `superStep`, `alternateTag`)
- `models/ExerciseRecord.kt` - Aggregates set + records + pagination hints
- `models/Record.kt` - Historical performance with volume calculation
- `models/WorkoutPlan.kt` - Workout program definition
- `repository/ExerciseRepository.kt` - Exercise data operations interface
- `repository/WorkoutPlanRepository.kt` - Workout plan operations interface
- `repository/SavedStateRepository.kt` - State persistence interface

## Dependencies

- Pure Kotlin (no Android)
- Kotlin Coroutines for Flow
- Paging 3 for pagination abstractions (exposed as `api`)

## Testing

```bash
./gradlew :data:test
```

## Used By

- `:room` - Implements repositories, maps entities to domain models
- `:dynamo` - Uses domain models for network serialization
- `:ui` - ViewModels depend on repository interfaces
- `:app` - Binds repository implementations
