# :dynamo Module

## Purpose

Network data access via AWS DynamoDB. Provides network service implementations for remote storage.

## Key Responsibilities

- Network service interfaces and DynamoDB implementations
- Network entities for serialization: `MutableExerciseSet`, `DynamoWorkoutPlan`
- Mapping between network entities and domain models

## Important Files

- `DynamoExerciseSetNetworkService.kt` - Implements `ExerciseSetNetworkService`
- `DynamoWorkoutPlanNetworkService.kt` - Implements `WorkoutPlanNetworkService`
- `entities/MutableExerciseSet.kt` - DynamoDB serialization model
- `entities/DynamoWorkoutPlan.kt` - DynamoDB serialization model

## Dependencies

- `api(project(":data"))` - Domain models
- `api(project(":util"))` - Shared utilities
- `implementation(libs.aws.android.sdk.*)` - Internal only

## Testing

```bash
./gradlew :dynamo:test
```

## Used By

- `:app` - Uses network services in repository implementations for remote sync
