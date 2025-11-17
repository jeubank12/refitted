# :room Module

## Purpose

Local persistence using Room database. Contains entities, DAOs, database configuration with extensive migration history (v1-v20), and SavedStateRepository implementation.

## Key Responsibilities

- Room entities: `RoomExercise`, `RoomExerciseSet`, `RoomSetRecord`, `RoomWorkoutPlan`, `RoomSavedState`
- DAOs with paging support: `ExerciseDao`, `WorkoutPlanDao`, `SavedStateDao`
- Database migrations and schema management
- Type converters for complex types

## Important Files

- `RefittedRoom.kt` - Database definition with all migrations
- `ExerciseDao.kt` - Exercise and set queries with `PagingSource`
- `WorkoutPlanDao.kt` - Workout plan queries
- `entities/RoomExerciseSet.kt` - Exercise sets table with foreign keys
- `entities/RoomSetRecord.kt` - Completed sets table
- `RoomSavedStateRepository.kt` - Implements `SavedStateRepository` from `:data`
- `Converters.kt` - JSON type converters

## Dependencies

- `api(project(":data"))` - Domain models and interfaces
- `api(libs.androidx.room.*)` - Exposed as api for :app repositories
- `api(libs.androidx.paging.runtime)` - Exposed as api for :app repositories

## Schema Location

`room/schemas/` - Contains v4-v12 schema exports for migration tracking

## Testing

```bash
./gradlew :room:test
```

## Used By

- `:app` - Provides DAOs, implements repositories using Room entities
