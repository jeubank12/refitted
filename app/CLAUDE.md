# :app Module

## Purpose

The `:app` module is the **glue/orchestration layer** that wires everything together. It contains the Android application entry points, Hilt dependency injection configuration, and repository implementations that bridge data sources.

## Module Type

**Android Application** - Main APK entry point

## Key Responsibilities

1. **Application Entry Point**: `RefittedApplication` and `RefittedComposeActivity`
2. **Dependency Injection**: Hilt modules providing all implementations
3. **Repository Implementations**: Bridge Room (local) + DynamoDB (remote)
4. **Module Orchestration**: Connect all feature modules together
5. **Build Configuration**: Debug/release variants, Firebase setup

## Dependencies

```gradle
dependencies {
    // All feature modules
    implementation(project(":data"))
    implementation(project(":room"))
    implementation(project(":dynamo"))
    implementation(project(":util"))
    implementation(project(":identity"))
    implementation(project(":ui"))

    // Android core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Firebase (configured via google-services.json)
    implementation(platform(libs.firebase.bom))

    // Testing
    testImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

**Depends on**: All other modules

## Structure

```
app/src/main/kotlin/com/litus_animae/refitted/
├── RefittedApplication.kt          # Application class
├── RefittedComposeActivity.kt      # Main activity
├── Constants.kt                    # App-wide constants
├── module/                         # Hilt DI modules
│   ├── RefittedRoomModule.kt       # Room provider binding
│   ├── ExerciseRepositoryModule.kt # Exercise repository binding
│   ├── WorkoutPlanRepositoryModule.kt  # Workout plan repository binding
│   ├── SavedStateRepositoryModule.kt   # Saved state repository binding
│   ├── ExerciseSetNetworkServiceModule.kt  # Network service binding
│   ├── WorkoutPlanNetworkServiceModule.kt  # Network service binding
│   └── IdentityModule.kt           # Auth/Config provider binding
├── data/
│   ├── room/
│   │   ├── RoomCacheExerciseRepository.kt      # ExerciseRepository impl
│   │   ├── RoomCacheWorkoutPlanRepository.kt   # WorkoutPlanRepository impl
│   │   ├── ExerciseSetPager.kt                 # Paging source for exercises
│   │   └── WorkoutPlanRemoteMediator.kt        # Remote mediator for sync
│   └── conversion/
│       └── DynamoToRoomConversionTest.kt       # Conversion testing
└── util/
    ├── ParameterizedResource.kt                 # Resource utilities
    ├── ParameterizedStringResource.kt
    ├── ParameterizedStringArrayResource.kt
    └── EmptyStringResource.kt
```

## Application Entry Points

### RefittedApplication
Application class with Hilt setup:

```kotlin
@HiltAndroidApp
class RefittedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        // Configure StrictMode (debug builds)
        // Set up crash reporting
    }
}
```

### RefittedComposeActivity
Main activity hosting Compose UI:

```kotlin
@AndroidEntryPoint
class RefittedComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RefittedTheme {
                // Provide ViewModels via Hilt
                val userViewModel: UserViewModel = hiltViewModel()
                val workoutViewModel: WorkoutViewModel = hiltViewModel()
                val exerciseViewModel: ExerciseViewModel = hiltViewModel()

                // Provide feature flags via CompositionLocal
                val features = configProvider.getFeatureFlags()
                CompositionLocalProvider(LocalFeatures provides features) {
                    RefittedApp(
                        userViewModel = userViewModel,
                        workoutViewModel = workoutViewModel,
                        exerciseViewModel = exerciseViewModel
                    )
                }
            }
        }
    }
}
```

## Repository Implementations

### RoomCacheExerciseRepository
Implements `ExerciseRepository` with offline-first caching:

```kotlin
class RoomCacheExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val networkService: ExerciseSetNetworkService
) : ExerciseRepository {

    override fun exerciseRecordsFlow(
        workoutPlanId: String,
        day: Int
    ): Flow<PagingData<ExerciseRecord>> {
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = {
                // Use Room as source
                ExerciseSetPager(exerciseDao, workoutPlanId, day)
            }
        ).flow.map { pagingData ->
            // Map Room entities to domain models
            pagingData.map { roomSet ->
                val records = getRecords(roomSet.id)
                ExerciseRecord(
                    exerciseSet = roomSet.toDomain(),
                    records = records,
                    // ... pagination hints
                )
            }
        }
    }

    override suspend fun saveRecord(
        exerciseSetId: String,
        weight: Double,
        reps: Int
    ) {
        // Save to Room immediately
        exerciseDao.insertRecord(
            RoomSetRecord(
                exerciseSetId = exerciseSetId,
                weight = weight,
                reps = reps,
                timestamp = System.currentTimeMillis()
            )
        )

        // Sync to network in background
        try {
            networkService.saveRecord(exerciseSetId, weight, reps)
        } catch (e: Exception) {
            // Log error, will retry on next sync
        }
    }

    // ... other methods
}
```

**Strategy**: Offline-first with eventual consistency
- Reads from Room (local cache)
- Writes to Room immediately, network asynchronously
- Uses `AsyncPagingDataDiffer` for pagination

### RoomCacheWorkoutPlanRepository
Implements `WorkoutPlanRepository` with `RemoteMediator`:

```kotlin
class RoomCacheWorkoutPlanRepository @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao,
    private val networkService: WorkoutPlanNetworkService
) : WorkoutPlanRepository {

    @OptIn(ExperimentalPagingApi::class)
    override fun workoutPlansFlow(): Flow<PagingData<WorkoutPlan>> {
        return Pager(
            config = PagingConfig(pageSize = 20),
            remoteMediator = WorkoutPlanRemoteMediator(
                networkService = networkService,
                database = workoutPlanDao
            ),
            pagingSourceFactory = { workoutPlanDao.getAll() }
        ).flow.map { pagingData ->
            // Map Room entities to domain models
            pagingData.map { it.toDomain() }
        }
    }

    // ... other methods
}
```

**Strategy**: Network-aware caching with `RemoteMediator`
- Fetches from network when cache is empty or stale
- Stores in Room for offline access
- Uses Paging 3 `RemoteMediator` pattern

### WorkoutPlanRemoteMediator
Coordinates network sync for workout plans:

```kotlin
@OptIn(ExperimentalPagingApi::class)
class WorkoutPlanRemoteMediator(
    private val networkService: WorkoutPlanNetworkService,
    private val database: WorkoutPlanDao
) : RemoteMediator<Int, RoomWorkoutPlan>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, RoomWorkoutPlan>
    ): MediatorResult {
        return try {
            val workoutPlans = networkService.getWorkoutPlans()

            // Store in database
            workoutPlans.forEach { plan ->
                database.insert(plan.toRoom())
            }

            MediatorResult.Success(endOfPaginationReached = true)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
```

## Hilt Modules

### RefittedRoomModule
Provides Room database:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object RefittedRoomModule {
    @Provides
    @Singleton
    fun provideRefittedRoomProvider(
        @ApplicationContext context: Context
    ): RefittedRoomProvider {
        return RefittedRoomProviderLive(context)
    }

    @Provides
    @Singleton
    fun provideExerciseDao(provider: RefittedRoomProvider): ExerciseDao {
        return provider.database().exerciseDao()
    }

    @Provides
    @Singleton
    fun provideWorkoutPlanDao(provider: RefittedRoomProvider): WorkoutPlanDao {
        return provider.database().workoutPlanDao()
    }

    @Provides
    @Singleton
    fun provideSavedStateDao(provider: RefittedRoomProvider): SavedStateDao {
        return provider.database().savedStateDao()
    }
}
```

### ExerciseRepositoryModule
Binds ExerciseRepository implementation:

```kotlin
@Module
@InstallIn(ViewModelComponent::class)
abstract class ExerciseRepositoryModule {
    @Binds
    abstract fun bindExerciseRepository(
        impl: RoomCacheExerciseRepository
    ): ExerciseRepository
}
```

### ExerciseSetNetworkServiceModule
Provides network services:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ExerciseSetNetworkServiceModule {
    @Provides
    @Singleton
    fun provideExerciseSetNetworkService(
        authProvider: AuthProvider,
        @ApplicationContext context: Context
    ): ExerciseSetNetworkService {
        return DynamoExerciseSetNetworkService(
            // Configure with AWS credentials from authProvider
            tableName = context.getString(R.string.dynamo_exercise_sets_table)
        )
    }
}
```

### IdentityModule
Provides Firebase Auth and Config:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object IdentityModule {
    @Provides
    @Singleton
    fun provideAuthProvider(): AuthProvider = AuthProviderLive()

    @Provides
    @Singleton
    fun provideConfigProvider(): ConfigProvider = ConfigProviderLive()
}
```

## Build Configuration

### Variants
- **debug**: Development build with StrictMode, test coverage, `.debug` suffix
- **release**: Production build with minification, ProGuard, resource shrinking

### Firebase Setup
- `google-services.json` required (from Firebase Console)
- Configured in `build.gradle` with `google-services` plugin

### ProGuard
Release builds use ProGuard rules for:
- Firebase preservation
- Room database preservation
- Hilt components preservation
- AWS SDK preservation

## Testing

Tests verify:
- Repository implementations (`RoomCacheExerciseRepositoryTest`)
- Entity conversions (`DynamoToRoomConversionTest`)

Run tests:
```bash
./gradlew :app:test
```

## Design Principles

1. **Glue Layer**: Connects modules without business logic
2. **Offline-First**: Local cache (Room) takes precedence
3. **Hilt DI**: All implementations provided via dependency injection
4. **Repository Pattern**: Data access abstraction
5. **Eventual Consistency**: Network sync happens asynchronously

## Module Dependencies Graph

```
:app
├── :ui (ViewModels, Compose UI)
│   ├── :data (domain models, repository interfaces)
│   ├── :identity (Auth, Config providers)
│   └── :util (utilities)
├── :room (Room database, DAOs, entities)
│   └── :data
├── :dynamo (Network services, DynamoDB)
│   ├── :data
│   └── :util
└── :identity (Firebase Auth/Config/Crashlytics)
```

The `:app` module is the only module that depends on `:room` and `:dynamo` together, allowing it to coordinate offline-first caching.
