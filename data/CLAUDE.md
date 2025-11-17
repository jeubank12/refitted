# :data Module

## Purpose

The `:data` module defines the **pure domain layer** of the application. It contains domain models, repository interfaces, and core business abstractions with no dependencies on Android framework or implementation details.

## Module Type

**Pure Kotlin Library** - No Android dependencies

## Key Responsibilities

1. **Domain Models**: Core business entities used throughout the app
2. **Repository Interfaces**: Contracts for data access without implementation details
3. **Business Logic**: Domain-level computed properties and methods

## Dependencies

```gradle
dependencies {
    // Kotlin stdlib only
    // Paging 3 for pagination abstractions
    // Kotlinx Coroutines for Flow
}
```

**No dependencies on other modules** - This is the foundation that other modules depend on.

## Structure

```
data/src/main/kotlin/com/litus_animae/refitted/data/
├── models/
│   ├── Exercise.kt              # Exercise definition
│   ├── ExerciseRecord.kt        # Aggregates set + records + pagination
│   ├── ExerciseSet.kt           # Exercise set with lazy exercise lookup
│   ├── Record.kt                # Historical performance record
│   ├── SavedState.kt            # Key-value state abstraction
│   └── WorkoutPlan.kt           # Workout program definition
└── repository/
    ├── ExerciseRepository.kt        # Exercise data operations
    ├── SavedStateRepository.kt      # State persistence
    └── WorkoutPlanRepository.kt     # Workout plan operations
```

## Domain Models

### Exercise
- `id: String` - Unique identifier
- `name: String` - Display name
- `muscleGroup: String?` - Primary muscle group

### ExerciseSet
- `id: String` - Unique identifier
- `exerciseId: String` - FK to Exercise
- `step: String` - Step identifier (e.g., "1.1.a" for supersets)
- `workoutPlanId: String` - FK to WorkoutPlan
- `day: Int` - Day of workout plan
- **Computed Properties**:
  - `isSuperSet: Boolean` - Parsed from step format
  - `primaryStep: String` - Main step number
  - `superStep: String?` - Superset indicator
  - `alternateTag: String?` - Alternate exercise marker

### ExerciseRecord
Aggregates exercise set data with historical records:
- `exerciseSet: ExerciseSet` - The exercise set
- `records: List<Record>` - Historical performance
- `isLastInSuperset: Boolean` - Pagination hint
- `isFirstInSuperset: Boolean` - Pagination hint

### Record
- `weight: Double` - Weight used
- `reps: Int` - Repetitions completed
- `timestamp: Long` - When performed
- **Computed**: `volume: Double` - weight × reps

### WorkoutPlan
- `id: String` - Unique identifier
- `name: String` - Program name
- `days: Int` - Total days in program
- `duration: Int` - Program length
- `startDate: Long` - When program starts
- `alternates: Map<String, List<String>>` - Alternate exercise mappings

## Repository Interfaces

### ExerciseRepository
```kotlin
interface ExerciseRepository {
    // Paged exercise records for a specific day
    fun exerciseRecordsFlow(workoutPlanId: String, day: Int): Flow<PagingData<ExerciseRecord>>

    // Current records for an exercise set
    fun getCurrentRecords(exerciseSetId: String): Flow<List<Record>>

    // Save completed set
    suspend fun saveRecord(exerciseSetId: String, weight: Double, reps: Int)

    // Get specific exercise by ID
    suspend fun getExercise(exerciseId: String): Exercise?
}
```

### WorkoutPlanRepository
```kotlin
interface WorkoutPlanRepository {
    // Paged list of all workout plans
    fun workoutPlansFlow(): Flow<PagingData<WorkoutPlan>>

    // Get specific workout plan
    suspend fun getWorkoutPlan(id: String): WorkoutPlan?

    // Save or update workout plan
    suspend fun save(workoutPlan: WorkoutPlan)
}
```

### SavedStateRepository
```kotlin
interface SavedStateRepository {
    // Get saved state value
    suspend fun get(key: String): String?

    // Save state value
    suspend fun save(key: String, value: String)
}
```

## Testing

Tests verify domain logic and model behavior:
- `ExerciseSetTest` - Step parsing logic, superset detection
- `ExerciseTest` - Exercise model behavior

Run tests:
```bash
./gradlew :data:test
```

## Design Principles

1. **No Android Dependencies**: Pure Kotlin for testability and reusability
2. **Interface-Based**: Repository interfaces allow multiple implementations
3. **Immutability**: All models are immutable data classes
4. **Flow-First**: Reactive streams for data observation
5. **Pagination-Aware**: Uses Paging 3 for large data sets

## Usage by Other Modules

- `:room` - Implements repositories, maps Room entities to domain models
- `:dynamo` - Uses domain models for network serialization
- `:ui` - ViewModels depend on repository interfaces
- `:app` - Binds repository implementations via Hilt
