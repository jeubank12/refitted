# :room Module

## Purpose

The `:room` module provides **local persistence** using Room database. It contains Room entities, DAOs, database configuration, and the SavedStateRepository implementation.

## Module Type

**Android Library** - Requires Android framework for Room

## Key Responsibilities

1. **Room Entities**: Database schema definitions with converters
2. **DAOs**: Data Access Objects for Room queries
3. **Database Configuration**: Room database setup with extensive migration history
4. **Repository Implementation**: SavedStateRepository using Room

## Dependencies

```gradle
dependencies {
    api(project(":data"))        // Domain models and interfaces

    // Room database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
}
```

**Depends on**: `:data` (domain models and repository interfaces)

## Structure

```
room/src/main/kotlin/com/litus_animae/refitted/room/
├── entities/
│   ├── RoomExercise.kt          # Exercise table
│   ├── RoomExerciseSet.kt       # Exercise sets table
│   ├── RoomSetRecord.kt         # Completed sets table
│   ├── RoomWorkoutPlan.kt       # Workout plans table
│   └── RoomSavedState.kt        # State persistence table
├── RefittedRoom.kt              # Database definition + migrations
├── ExerciseDao.kt               # Exercise and set queries
├── WorkoutPlanDao.kt            # Workout plan queries
├── SavedStateDao.kt             # State queries
├── RoomSavedStateRepository.kt  # SavedStateRepository impl
├── Converters.kt                # Type converters for Room
├── RefittedRoomProvider.kt      # Database provider interface
└── RefittedRoomProviderLive.kt  # Live database provider impl
```

## Room Entities

### RoomExercise
```kotlin
@Entity(tableName = "exercises")
data class RoomExercise(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String?
)
```

### RoomExerciseSet
```kotlin
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(entity = RoomExercise::class, ...),
        ForeignKey(entity = RoomWorkoutPlan::class, ...)
    ],
    indices = [...]
)
data class RoomExerciseSet(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val step: String,               // e.g., "1.1.a"
    val workoutPlanId: String,
    val day: Int,
    val sets: Int,
    val reps: Int,
    val weight: Double
)
```

### RoomSetRecord
```kotlin
@Entity(
    tableName = "set_records",
    foreignKeys = [ForeignKey(entity = RoomExerciseSet::class, ...)],
    indices = [...]
)
data class RoomSetRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseSetId: String,
    val weight: Double,
    val reps: Int,
    val timestamp: Long
)
```

### RoomWorkoutPlan
```kotlin
@Entity(tableName = "workout_plans")
data class RoomWorkoutPlan(
    @PrimaryKey val id: String,
    val name: String,
    val days: Int,
    val duration: Int,
    val startDate: Long,
    @TypeConverters(Converters::class)
    val alternates: Map<String, List<String>>
)
```

### RoomSavedState
```kotlin
@Entity(tableName = "saved_state")
data class RoomSavedState(
    @PrimaryKey val key: String,
    val value: String
)
```

## DAOs

### ExerciseDao
```kotlin
@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise_sets WHERE workoutPlanId = :workoutPlanId AND day = :day")
    fun getExerciseSetsForDay(workoutPlanId: String, day: Int): PagingSource<Int, RoomExerciseSet>

    @Query("SELECT * FROM set_records WHERE exerciseSetId = :exerciseSetId ORDER BY timestamp DESC")
    fun getRecordsForSet(exerciseSetId: String): Flow<List<RoomSetRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: RoomExercise)

    @Insert
    suspend fun insertRecord(record: RoomSetRecord)

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: String): RoomExercise?
}
```

### WorkoutPlanDao
```kotlin
@Dao
interface WorkoutPlanDao {
    @Query("SELECT * FROM workout_plans ORDER BY startDate DESC")
    fun getAll(): PagingSource<Int, RoomWorkoutPlan>

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun get(id: String): RoomWorkoutPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workoutPlan: RoomWorkoutPlan)
}
```

### SavedStateDao
```kotlin
@Dao
interface SavedStateDao {
    @Query("SELECT value FROM saved_state WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: RoomSavedState)
}
```

## Database Configuration

### RefittedRoom
Room database with **extensive migration history**:

```kotlin
@Database(
    entities = [
        RoomExercise::class,
        RoomExerciseSet::class,
        RoomSetRecord::class,
        RoomWorkoutPlan::class,
        RoomSavedState::class
    ],
    version = 20,  // Multiple migrations defined
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RefittedRoom : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutPlanDao(): WorkoutPlanDao
    abstract fun savedStateDao(): SavedStateDao
}
```

**Migration Strategy**: All migrations from version 1 → 20 are defined in `RefittedRoom.kt`. When modifying entities, always provide a migration path.

**Schema Location**: `room/schemas/` (configured via KSP args in build.gradle)

## Type Converters

### Converters.kt
```kotlin
class Converters {
    @TypeConverter
    fun fromMap(value: Map<String, List<String>>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toMap(value: String): Map<String, List<String>> {
        return Json.decodeFromString(value)
    }
}
```

## Repository Implementation

### RoomSavedStateRepository
Implements `SavedStateRepository` from `:data`:

```kotlin
class RoomSavedStateRepository @Inject constructor(
    private val dao: SavedStateDao
) : SavedStateRepository {
    override suspend fun get(key: String): String? = dao.get(key)

    override suspend fun save(key: String, value: String) {
        dao.save(RoomSavedState(key, value))
    }
}
```

## Testing

Tests verify Room entity behavior:
- `RoomExerciseSetTest` - Step parsing, superset detection in Room entities

Run tests:
```bash
./gradlew :room:test
```

## Design Principles

1. **Single Responsibility**: Only concerned with local persistence
2. **Entity Isolation**: Room entities are internal to this module
3. **Foreign Keys**: Enforced referential integrity
4. **Indices**: Optimized for common queries
5. **Migration Safety**: All schema changes have migration paths

## Usage by :app Module

The `:app` module:
- Provides `RefittedRoomProvider` via Hilt
- Implements `ExerciseRepository` and `WorkoutPlanRepository` using these DAOs
- Maps between Room entities and domain models from `:data`
