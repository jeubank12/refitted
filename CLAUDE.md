# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Refitted is a fitness tracking Android application built with Kotlin and Jetpack Compose, with a complementary Next.js web interface. The app helps users follow workout programs, track exercise sets, and monitor progress over time.

## Build Commands

### Android App
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Build and run all tests
./gradlew build

# Clean build artifacts
./gradlew clean
```

### Web Interface
```bash
cd web
npm ci
npm run build
npm run lint
```

### Admin Lambda Functions
```bash
cd admin
npm ci
npm run build <path-to-lambda-file>
npm run lint
```

## Testing

### Unit Tests
```bash
# Run all unit tests
./gradlew test

# Run debug unit tests only
./gradlew testDebugUnitTest

# Run release unit tests only
./gradlew testReleaseUnitTest

# Run a specific test class
./gradlew testDebugUnitTest --tests "com.litus_animae.refitted.data.room.RoomCacheExerciseRepositoryTest"

# Run a specific test method
./gradlew testDebugUnitTest --tests "*.RoomCacheExerciseRepositoryTest.getCurrentRecords*"

# Debug tests (suspends on port 5005)
./gradlew testDebugUnitTest --debug-jvm
```

**Test Framework**: JUnit 5 (Jupiter) with:
- MockK for mocking
- Google Truth for assertions
- Turbine for Flow testing
- Kotlin Coroutines Test

**Test Organization**: Tests use `@Nested` and `@DisplayName` for grouping, with backtick-style test names describing behavior.

### Instrumentation Tests
```bash
# Run tests on connected device/emulator
./gradlew connectedAndroidTest

# Run debug instrumentation tests
./gradlew connectedDebugAndroidTest

# Install test APK
./gradlew installDebugAndroidTest
```

### Code Quality
```bash
# Run lint checks
./gradlew lint

# Run lint on specific variant
./gradlew lintDebug

# Auto-fix lint issues
./gradlew lintFix

# Update lint baseline
./gradlew updateLintBaseline
```

## Architecture

### MVVM with Repository Pattern

**Layers (bottom to top):**

1. **Data Sources**
   - **Room Database** (`RefittedRoom`): Local SQLite cache with extensive migration history
   - **Network Services** (DynamoDB): AWS-backed remote storage via `DynamoExerciseSetNetworkService` and `DynamoWorkoutPlanNetworkService`

2. **Data Access (DAOs)**
   - `ExerciseDao`: Exercise sets, records, pagination
   - `WorkoutPlanDao`: Workout plans with paging
   - `SavedStateDao`: Key-value state persistence

3. **Repositories** (abstraction layer)
   - `ExerciseRepository` → `RoomCacheExerciseRepository`: Manages exercises with offline-first caching using `AsyncPagingDataDiffer`
   - `WorkoutPlanRepository` → `RoomCacheWorkoutPlanRepository`: Paged workout plans with `RemoteMediator` for network sync
   - `SavedStateRepository` → `RoomSavedStateRepository`: Simple state persistence

4. **ViewModels** (business logic, Hilt-injected)
   - `ExerciseViewModel`: Exercise flow, alternate exercises, complex Flow combinations
   - `WorkoutViewModel`: Workout selection, progress tracking, uses `SavedStateHandle`
   - `UserViewModel`: Firebase authentication, feature flags

5. **UI (Jetpack Compose)**
   - `compose/calendar/`: Workout selection and calendar view
   - `compose/exercise/`: Exercise execution interface with sets, reps, weight input
   - Custom saveable state objects (`Weight.Saver`, `Repetitions.Saver`)

### Dependency Injection (Hilt)

All modules are in `module/` package:
- `RefittedRoomModule`: Binds Room database provider (`@Singleton`)
- `ExerciseRepositoryModule`: Binds repository implementations (`@ViewModelComponent`)
- `WorkoutPlanRepositoryModule`, `SavedStateRepositoryModule`, `ExerciseSetNetworkServiceModule`, `LogModule`

ViewModels use `@HiltViewModel` with constructor injection. Entry point is `RefittedComposeActivity` with `@AndroidEntryPoint`.

### Key Domain Concepts

**Exercise Set Parsing**: The app supports complex workout notation including:
- **Supersets**: Exercises grouped together (e.g., step "1.1.a" indicates superset alternates)
- **Alternates**: Exercise variations parsed from step identifiers using regex
- Logic implemented in `ExerciseSet` (computed properties: `isSuperSet`, `primaryStep`, `superStep`, etc.)

**Pagination Strategy**:
- Uses Android Paging 3 library throughout
- `AsyncPagingDataDiffer` in `RoomCacheExerciseRepository` for managing paginated lists
- `RemoteMediator` pattern in `WorkoutPlanRemoteMediator` for network-aware caching
- `PagingSource` implementations in DAOs (`ExerciseSetPager`)

**Flow-Based Reactivity**:
- Heavy use of Kotlin `Flow` and `StateFlow` for reactive data streams
- Complex flow transformations: `combine()`, `flatMapLatest()`, `map()`, `mapNotNull()`
- All repositories expose `Flow<T>` for observing data changes

### Data Models

**Room Entities**:
- `Exercise`: Exercise definitions
- `RoomExerciseSet`: Exercise sets with step parsing logic
- `SetRecord`: Completed set data (weight, reps, timestamp)
- `WorkoutPlan`: Workout programs (days, duration, start date, alternates)
- `SavedState`: Key-value persistence

**Domain Models**:
- `ExerciseSet`: Wraps `RoomExerciseSet` with lazy `Flow<Exercise?>` lookup
- `ExerciseRecord`: Aggregates exercise set + historical records + pagination info
- `Record`: Domain model with accumulated stats

**Network Models** (in `models/dynamo/`):
- `MutableExerciseSet`, `DynamoWorkoutPlan`: Serialization models for DynamoDB

### Firebase Integration

- **Authentication**: Google Sign-In via `AuthProvider`, anonymous fallback
- **Remote Config**: Feature flags via `ConfigProvider`
- **Crashlytics**: Error tracking
- Configuration exposed to Compose via `CompositionLocal` (`LocalFeatures`)

## Package Structure

```
app/src/main/java/com/litus_animae/refitted/
├── compose/              # Jetpack Compose UI
│   ├── calendar/         # Workout selection screen
│   ├── exercise/         # Exercise execution screen
│   │   ├── input/        # Weight/reps input controls
│   │   └── set/          # Exercise set display
│   ├── charts/           # Data visualization
│   ├── state/            # Reusable compose state
│   └── util/             # Compose utilities
├── data/                 # Data layer
│   ├── room/             # Room DAOs, repositories, paging
│   ├── dynamo/           # AWS DynamoDB network services
│   └── network/          # Network service interfaces
├── models/               # ViewModels and domain models
│   └── dynamo/           # Network serialization models
├── module/               # Hilt dependency injection modules
└── util/                 # Utilities (IterableUtil, LogUtil, etc.)
```

## Important Files

| Component | Path |
|-----------|------|
| Application Entry | `RefittedApplication.kt`, `RefittedComposeActivity.kt` |
| Database | `data/room/RefittedRoom.kt` |
| Repositories | `data/room/RoomCacheExerciseRepository.kt`, `data/room/RoomCacheWorkoutPlanRepository.kt` |
| ViewModels | `models/ExerciseViewModel.kt`, `models/WorkoutViewModel.kt`, `models/UserViewModel.kt` |
| Main Screens | `compose/calendar/Main.kt`, `compose/exercise/Main.kt` |
| Navigation | `compose/Top.kt` |

## Development Conventions

### Code Style
- Uses Kotlin coroutines with `suspend` functions and `Flow` extensively
- Prefer `Flow` over LiveData for reactive streams
- Repository pattern for data abstraction
- Use Hilt for dependency injection (avoid manual factory creation)

### Testing Patterns
```kotlin
@Nested
@DisplayName("Feature description")
inner class FeatureTests {
    @Test
    fun `specific behavior description`() = runTest {
        // Given
        val setup = mockData()

        // When
        val result = subject.methodUnderTest(setup)

        // Then
        assertThat(result).isEqualTo(expected)
    }
}
```

**Flow Testing** with Turbine:
```kotlin
repository.someFlow.test {
    assertThat(awaitItem()).isEqualTo(expectedValue)
}
```

### Database Migrations
- Room database has extensive migration history with all migrations defined in `RefittedRoom.kt`
- When modifying entities, always provide a migration path
- Room schema location: `app/schemas/` (configured in build.gradle KSP args)
- DAO implementations are generated by KSP and should not be manually modified

### Compose State Management
- Use `rememberSaveable` with custom `Saver` implementations for state that should survive configuration changes
- Examples: `Weight.Saver`, `Repetitions.Saver` in `compose/exercise/set/`
- Prefer `StateFlow` in ViewModels, collect as state in Compose: `viewModel.flow.collectAsState()`

### Build Variants
- **Debug**: Includes `.debug` suffix, test coverage enabled, StrictMode configured
- **Release**: Minification enabled with ProGuard, resources shrunk, full debug symbols for NDK

## CI/CD

GitHub Actions workflows (`.github/workflows/build.yml`):
- **Android**: Builds on JDK 17, requires `google-services.json` secret, runs `assembleRelease`
- **Web**: Node.js 20.15, requires Firebase/AWS secrets, runs `npm run build` and `npm run lint`
- **Admin**: Lambda function builds, matrix strategy for changed files only
- **Checks**: Blocks fixup commit merges, path-based job filtering

## Multi-Module Project

This repository contains three components:
1. **app/** - Android application (Kotlin + Compose)
2. **web/** - Next.js web interface (TypeScript + React + Redux + Material-UI)
3. **admin/** - AWS Lambda functions (TypeScript)

All three share the same AWS DynamoDB backend for data storage.
