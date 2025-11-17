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

### Multi-Module Architecture

The Android app uses **modular architecture** with clear separation of concerns. Each module has specific responsibilities and dependencies are managed via Gradle module dependencies and Hilt DI.

**Module Dependency Graph:**
```
:app (Glue Layer)
├── :ui (Presentation)
│   ├── :data (Domain)
│   ├── :identity (Firebase)
│   └── :util (Utilities)
├── :room (Local Persistence)
│   └── :data
├── :dynamo (Network)
│   ├── :data
│   └── :util
└── :identity
```

**For detailed module documentation, see:**
- `data/CLAUDE.md` - Domain models and repository interfaces
- `room/CLAUDE.md` - Room database, entities, DAOs
- `dynamo/CLAUDE.md` - DynamoDB network services
- `util/CLAUDE.md` - Shared utility functions
- `identity/CLAUDE.md` - Firebase Auth, Config, Crashlytics
- `ui/CLAUDE.md` - ViewModels, Jetpack Compose UI
- `app/CLAUDE.md` - Application entry point, DI, repository implementations

### MVVM with Repository Pattern

**Layers (bottom to top):**

1. **Domain Layer** (`:data` module)
   - Pure Kotlin domain models: `Exercise`, `ExerciseSet`, `WorkoutPlan`, `Record`
   - Repository interfaces: `ExerciseRepository`, `WorkoutPlanRepository`, `SavedStateRepository`
   - No Android or implementation dependencies

2. **Data Sources**
   - **:room module**: Local SQLite cache with Room database, extensive migration history
   - **:dynamo module**: Network access via AWS DynamoDB services
   - **:identity module**: Firebase Auth, Remote Config, Crashlytics

3. **Repository Implementations** (`:app` module - glue layer)
   - `RoomCacheExerciseRepository`: Offline-first caching bridging Room + DynamoDB
   - `RoomCacheWorkoutPlanRepository`: Network-aware caching with `RemoteMediator`
   - Implements domain interfaces, coordinates data sources

4. **Presentation** (`:ui` module)
   - **ViewModels** (business logic, Hilt-injected):
     - `ExerciseViewModel`: Exercise flow, alternate exercises, complex Flow combinations
     - `WorkoutViewModel`: Workout selection, progress tracking, uses `SavedStateHandle`
     - `UserViewModel`: Firebase authentication, feature flags
   - **Jetpack Compose UI**:
     - `compose/calendar/`: Workout selection and calendar view
     - `compose/exercise/`: Exercise execution interface with sets, reps, weight input
     - Custom saveable state objects (`Weight.Saver`, `Repetitions.Saver`)

### Dependency Injection (Hilt)

All Hilt modules are in `:app/module/` package:
- `RefittedRoomModule`: Provides Room database and DAOs (`@Singleton`)
- `ExerciseRepositoryModule`: Binds repository implementations (`@ViewModelComponent`)
- `WorkoutPlanRepositoryModule`, `SavedStateRepositoryModule`
- `ExerciseSetNetworkServiceModule`, `WorkoutPlanNetworkServiceModule`
- `IdentityModule`: Provides `AuthProvider` and `ConfigProvider`

ViewModels in `:ui` use `@HiltViewModel` with constructor injection. Application entry point is `RefittedComposeActivity` in `:app` with `@AndroidEntryPoint`.

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

**Domain Models** (`:data` module):
- `Exercise`: Exercise definitions
- `ExerciseSet`: Exercise set with step parsing logic
- `ExerciseRecord`: Aggregates exercise set + historical records + pagination info
- `Record`: Historical performance record with volume calculation
- `WorkoutPlan`: Workout programs (days, duration, start date, alternates)
- `SavedState`: Key-value persistence abstraction

**Room Entities** (`:room` module):
- `RoomExercise`, `RoomExerciseSet`, `RoomSetRecord`, `RoomWorkoutPlan`, `RoomSavedState`
- Internal to `:room` module, mapped to domain models by repository implementations

**Network Entities** (`:dynamo` module):
- `MutableExerciseSet`, `DynamoWorkoutPlan`: Serialization models for DynamoDB
- Internal to `:dynamo` module, mapped to domain models by network services

### Firebase Integration (`:identity` module)

- **Authentication**: Google Sign-In via `AuthProvider`, anonymous fallback
- **Remote Config**: Feature flags via `ConfigProvider`
- **Crashlytics**: Error tracking
- Abstractions allow testing with mocks
- Configuration exposed to `:ui` Compose via `CompositionLocal` (`LocalFeatures`)

## Module Organization

The Android app is organized into the following Gradle modules:

### Core Modules

- **:data** - Pure Kotlin domain layer with models and repository interfaces
- **:util** - Shared utility functions (collection helpers, logging)

### Data Source Modules

- **:room** - Room database for local persistence (SQLite)
- **:dynamo** - DynamoDB network services for remote storage
- **:identity** - Firebase integration (Auth, Remote Config, Crashlytics)

### Presentation Module

- **:ui** - ViewModels and Jetpack Compose UI

### Application Module

- **:app** - Application entry point, Hilt DI configuration, repository implementations

**Dependency Flow**: `:app` coordinates all modules. `:data` is the foundation with no dependencies. `:ui`, `:room`, and `:dynamo` depend on `:data`. `:app` bridges `:room` and `:dynamo` to implement offline-first repositories.

**For detailed documentation on each module, see the respective `CLAUDE.md` files:**
- `data/CLAUDE.md`
- `room/CLAUDE.md`
- `dynamo/CLAUDE.md`
- `util/CLAUDE.md`
- `identity/CLAUDE.md`
- `ui/CLAUDE.md`
- `app/CLAUDE.md`

## Important Files

| Component | Module | Path |
|-----------|--------|------|
| Application Entry | :app | `app/.../RefittedApplication.kt`, `RefittedComposeActivity.kt` |
| Database | :room | `room/.../RefittedRoom.kt` |
| Domain Models | :data | `data/.../models/*.kt` |
| Repository Interfaces | :data | `data/.../repository/*.kt` |
| Repository Implementations | :app | `app/.../data/room/RoomCache*Repository.kt` |
| ViewModels | :ui | `ui/.../models/*ViewModel.kt` |
| Main Screens | :ui | `ui/.../compose/calendar/Main.kt`, `compose/exercise/Main.kt` |
| Navigation | :ui | `ui/.../compose/Top.kt` |
| DAOs | :room | `room/.../*Dao.kt` |
| Network Services | :dynamo | `dynamo/.../Dynamo*NetworkService.kt` |
| Firebase Providers | :identity | `identity/.../*Provider.kt` |

Note: Paths abbreviated with `...` represent the full package path `com/litus_animae/refitted/`

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
- Room database has extensive migration history (v1-v20) defined in `:room/RefittedRoom.kt`
- When modifying Room entities, always provide a migration path
- Room schema location: `:room/schemas/` (configured in build.gradle KSP args)
- DAO implementations are generated by KSP and should not be manually modified

### Compose State Management
- Use `rememberSaveable` with custom `Saver` implementations for state that should survive configuration changes
- Examples in `:ui` module: `Weight.Saver`, `Repetitions.Saver` in `compose/state/`
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

## Repository Components

This repository contains three separate applications:

1. **Android App** (multi-module Kotlin + Compose)
   - Modules: `:app`, `:ui`, `:data`, `:room`, `:dynamo`, `:util`, `:identity`
   - See module-specific `CLAUDE.md` files for details

2. **web/** - Next.js web interface (TypeScript + React + Redux + Material-UI)
   - Separate web application sharing DynamoDB backend

3. **admin/** - AWS Lambda functions (TypeScript)
   - Backend administration functions

All three components share the same AWS DynamoDB backend for data storage.

## Code Conventions

- Avoid fully qualified class names in code. Prefer file imports and use renames in the import syntax
- Keep modules focused: domain logic in `:data`, persistence in `:room`, UI in `:ui`, etc.
- Repository implementations in `:app` bridge multiple data sources (Room + DynamoDB)