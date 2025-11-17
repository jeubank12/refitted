# :dynamo Module

## Purpose

The `:dynamo` module provides **network data access** via AWS DynamoDB. It contains network service interfaces, implementations, and DynamoDB-specific entity models for serialization.

## Module Type

**Android Library** - Requires Android framework for AWS SDK

## Key Responsibilities

1. **Network Services**: Interface definitions for data operations
2. **DynamoDB Implementation**: AWS DynamoDB SDK integration
3. **Network Entities**: Serialization models for DynamoDB
4. **Data Mapping**: Convert between network entities and domain models

## Dependencies

```gradle
dependencies {
    api(project(":data"))        // Domain models
    api(project(":util"))        // Shared utilities

    // AWS SDK
    implementation(libs.aws.dynamodb)
    implementation(libs.aws.core)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization)
}
```

**Depends on**: `:data` (domain models), `:util` (utilities)

## Structure

```
dynamo/src/main/kotlin/com/litus_animae/refitted/dynamo/
├── entities/
│   ├── MutableExerciseSet.kt    # DynamoDB exercise set entity
│   └── DynamoWorkoutPlan.kt     # DynamoDB workout plan entity
├── DynamoExerciseSetNetworkService.kt   # Exercise set network ops
├── DynamoWorkoutPlanNetworkService.kt   # Workout plan network ops
├── DynamoNetworkService.kt      # Base DynamoDB service
├── ExerciseSetNetworkService.kt # Exercise set interface
└── WorkoutPlanNetworkService.kt # Workout plan interface
```

## Network Service Interfaces

### ExerciseSetNetworkService
```kotlin
interface ExerciseSetNetworkService {
    // Fetch exercise sets for a workout plan day
    suspend fun getExerciseSets(workoutPlanId: String, day: Int): List<ExerciseSet>

    // Save a completed set record
    suspend fun saveRecord(exerciseSetId: String, weight: Double, reps: Int)

    // Sync local data to remote
    suspend fun syncExerciseSets(exerciseSets: List<ExerciseSet>)
}
```

### WorkoutPlanNetworkService
```kotlin
interface WorkoutPlanNetworkService {
    // Fetch all workout plans
    suspend fun getWorkoutPlans(): List<WorkoutPlan>

    // Fetch a specific workout plan
    suspend fun getWorkoutPlan(id: String): WorkoutPlan?

    // Save or update a workout plan
    suspend fun saveWorkoutPlan(workoutPlan: WorkoutPlan)
}
```

## DynamoDB Implementations

### DynamoExerciseSetNetworkService
Implements `ExerciseSetNetworkService` using AWS DynamoDB:

```kotlin
class DynamoExerciseSetNetworkService @Inject constructor(
    private val dynamoDb: DynamoDBClient,
    private val tableName: String
) : ExerciseSetNetworkService {

    override suspend fun getExerciseSets(
        workoutPlanId: String,
        day: Int
    ): List<ExerciseSet> {
        // Query DynamoDB with partition key = workoutPlanId, sort key = day
        // Convert MutableExerciseSet entities to domain ExerciseSet models
    }

    override suspend fun saveRecord(
        exerciseSetId: String,
        weight: Double,
        reps: Int
    ) {
        // Save set_record to DynamoDB
    }
}
```

### DynamoWorkoutPlanNetworkService
Implements `WorkoutPlanNetworkService` using AWS DynamoDB:

```kotlin
class DynamoWorkoutPlanNetworkService @Inject constructor(
    private val dynamoDb: DynamoDBClient,
    private val tableName: String
) : WorkoutPlanNetworkService {

    override suspend fun getWorkoutPlans(): List<WorkoutPlan> {
        // Scan workout_plans table
        // Convert DynamoWorkoutPlan entities to domain WorkoutPlan models
    }

    override suspend fun getWorkoutPlan(id: String): WorkoutPlan? {
        // Get item by id
    }

    override suspend fun saveWorkoutPlan(workoutPlan: WorkoutPlan) {
        // Put item to DynamoDB
    }
}
```

## Network Entities

### MutableExerciseSet
Mutable entity for DynamoDB serialization:

```kotlin
@Serializable
data class MutableExerciseSet(
    var id: String = "",
    var exerciseId: String = "",
    var step: String = "",
    var workoutPlanId: String = "",
    var day: Int = 0,
    var sets: Int = 0,
    var reps: Int = 0,
    var weight: Double = 0.0
) {
    // Convert to domain ExerciseSet
    fun toExerciseSet(): ExerciseSet = ExerciseSet(...)

    companion object {
        // Create from domain ExerciseSet
        fun fromExerciseSet(set: ExerciseSet): MutableExerciseSet = MutableExerciseSet(...)
    }
}
```

### DynamoWorkoutPlan
DynamoDB representation of WorkoutPlan:

```kotlin
@Serializable
data class DynamoWorkoutPlan(
    var id: String = "",
    var name: String = "",
    var days: Int = 0,
    var duration: Int = 0,
    var startDate: Long = 0,
    var alternates: Map<String, List<String>> = emptyMap()
) {
    fun toWorkoutPlan(): WorkoutPlan = WorkoutPlan(...)

    companion object {
        fun fromWorkoutPlan(plan: WorkoutPlan): DynamoWorkoutPlan = DynamoWorkoutPlan(...)
    }
}
```

## Base Service

### DynamoNetworkService
Base class providing common DynamoDB operations:

```kotlin
abstract class DynamoNetworkService(
    protected val dynamoDb: DynamoDBClient,
    protected val tableName: String
) {
    protected suspend fun <T> queryItems(
        partitionKey: String,
        sortKey: String? = null
    ): List<T>

    protected suspend fun <T> getItem(id: String): T?

    protected suspend fun <T> putItem(item: T)
}
```

## Configuration

DynamoDB configuration is provided by `:app` module via Hilt:
- Table names from configuration
- AWS credentials from `:identity` module (via AuthProvider)
- Region configuration

## Design Principles

1. **Interface-Based**: Service interfaces allow multiple implementations (DynamoDB, mock, etc.)
2. **Entity Mapping**: Separate network entities from domain models
3. **Async-First**: All operations are suspend functions
4. **Error Handling**: Proper exception handling for network failures
5. **Serialization**: Uses Kotlinx Serialization for JSON

## Usage by :app Module

The `:app` module:
- Provides DynamoDB client configuration via Hilt
- Uses these services in repository implementations
- Implements offline-first strategy (Room cache + DynamoDB sync)
