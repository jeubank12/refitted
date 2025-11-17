# :ui Module

## Purpose

The `:ui` module contains the **presentation layer** including ViewModels (business logic) and Jetpack Compose UI (view layer). It provides the complete user interface for the fitness tracking app.

## Module Type

**Android Library** - Requires Android framework and Jetpack Compose

## Key Responsibilities

1. **ViewModels**: Business logic, state management, repository coordination
2. **Compose UI**: Declarative UI components for all screens
3. **Navigation**: Screen routing and navigation logic
4. **UI State**: Saveable state objects for configuration changes
5. **Resources**: UI strings, drawables, fonts, changelog

## Dependencies

```gradle
dependencies {
    api(project(":data"))            // Domain models and repositories
    implementation(project(":util")) // Utilities
    implementation(project(":identity")) // Auth and config providers

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Hilt for DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Paging Compose
    implementation(libs.paging.compose)

    // Firebase (for types only - implementations from :identity)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)
}
```

**Depends on**: `:data`, `:util`, `:identity`

## Structure

```
ui/src/main/kotlin/com/litus_animae/refitted/ui/
├── models/
│   ├── ExerciseViewModel.kt         # Exercise execution logic
│   ├── WorkoutViewModel.kt          # Workout selection logic
│   ├── UserViewModel.kt             # Authentication logic
│   └── ExerciseStep.kt              # UI step model
├── compose/
│   ├── Top.kt                       # Main navigation
│   ├── AuthButton.kt                # Google Sign-In button
│   ├── Changelog.kt                 # Version changelog dialog
│   ├── CompositionLocalFeatures.kt  # Feature flag access
│   ├── calendar/
│   │   ├── Main.kt                  # Workout selection screen
│   │   ├── Calendar.kt              # Calendar widget
│   │   └── WorkoutMenu.kt           # Workout menu dialog
│   ├── exercise/
│   │   ├── Main.kt                  # Exercise execution screen
│   │   ├── Exercise.kt              # Exercise display
│   │   ├── ExerciseTimer.kt         # Timer overlay
│   │   ├── Instruction.kt           # Exercise instructions
│   │   ├── Menu.kt                  # Exercise menu dialog
│   │   ├── RecordList.kt            # Historical records
│   │   ├── RepsDisplay.kt           # Reps counter
│   │   ├── SetsDisplay.kt           # Sets progress
│   │   ├── Timer.kt                 # Timer component
│   │   ├── WeightDisplay.kt         # Weight display
│   │   ├── input/
│   │   │   ├── RepetitionsButtons.kt    # Reps input
│   │   │   ├── ValueText.kt             # Value display
│   │   │   └── WeightButtons.kt         # Weight input
│   │   └── set/
│   │       ├── CompleteSetButton.kt     # Complete set action
│   │       ├── ExerciseSetView.kt       # Set display
│   │       └── RestTimer.kt             # Rest timer
│   ├── charts/
│   │   ├── BubbleChart.kt           # Volume bubble chart
│   │   ├── BubbleChartExploded.kt   # Detailed bubble chart
│   │   └── LineChart.kt             # Progress line chart
│   ├── state/
│   │   ├── Repetitions.kt           # Saveable reps state
│   │   ├── SetsRecords.kt           # Saveable sets state
│   │   └── Weight.kt                # Saveable weight state
│   └── util/
│       ├── ConstrainedButton.kt     # Sized button component
│       ├── LoadingScreen.kt         # Loading indicator
│       ├── NumberPicker.kt          # Number picker widget
│       └── Theme.kt                 # Material 3 theme
└── util/
    └── MonadUtil.kt                 # Monad utilities for UI
```

## ViewModels

### ExerciseViewModel
Manages exercise execution flow:

```kotlin
@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Current exercise records (paged)
    val exerciseRecords: Flow<PagingData<ExerciseRecord>>

    // Current set being performed
    val currentSet: StateFlow<ExerciseSet?>

    // Historical records for current exercise
    val currentRecords: StateFlow<List<Record>>

    // Complete current set
    suspend fun completeSet(weight: Double, reps: Int)

    // Move to next exercise
    fun nextExercise()

    // Handle alternate exercise selection
    fun selectAlternate(exerciseId: String)
}
```

**Key Features**:
- Complex Flow combinations with `combine()`, `flatMapLatest()`
- Superset detection and alternate exercise handling
- Pagination integration with Paging 3

### WorkoutViewModel
Manages workout selection and progress:

```kotlin
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val savedStateRepository: SavedStateRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Available workout plans (paged)
    val workoutPlans: Flow<PagingData<WorkoutPlan>>

    // Selected workout plan
    val selectedWorkoutPlan: StateFlow<WorkoutPlan?>

    // Current day in workout
    val currentDay: StateFlow<Int>

    // Select workout plan
    fun selectWorkoutPlan(workoutPlanId: String)

    // Advance to next day
    fun nextDay()

    // Reset progress
    fun resetProgress()
}
```

**Key Features**:
- Uses `SavedStateHandle` for process death survival
- Persists workout progress via SavedStateRepository

### UserViewModel
Manages authentication and user state:

```kotlin
@HiltViewModel
class UserViewModel @Inject constructor(
    private val authProvider: AuthProvider,
    private val configProvider: ConfigProvider
) : ViewModel() {

    // Current user
    val user: StateFlow<FirebaseUser?>

    // Feature flags from Remote Config
    val featureFlags: StateFlow<ConfigProvider.RemoteConfig>

    // Sign in with Google
    suspend fun signInWithGoogle(idToken: String)

    // Sign out
    fun signOut()

    // Fetch remote config
    suspend fun fetchRemoteConfig()
}
```

**Key Features**:
- Google Sign-In with Firebase Auth
- Anonymous user handling and upgrade
- Remote Config feature flags

## Compose UI

### Navigation (Top.kt)
Main navigation between screens:

```kotlin
@Composable
fun RefittedApp(
    userViewModel: UserViewModel,
    workoutViewModel: WorkoutViewModel,
    exerciseViewModel: ExerciseViewModel
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "calendar") {
        composable("calendar") {
            CalendarScreen(
                workoutViewModel = workoutViewModel,
                onWorkoutSelected = { navController.navigate("exercise") }
            )
        }
        composable("exercise") {
            ExerciseScreen(
                exerciseViewModel = exerciseViewModel,
                onComplete = { navController.popBackStack() }
            )
        }
    }
}
```

### Calendar Screen (calendar/Main.kt)
Workout selection interface:
- Calendar widget showing workout days
- Workout plan dropdown
- Progress indicators
- Navigation to exercise execution

### Exercise Screen (exercise/Main.kt)
Exercise execution interface:
- Current exercise display with animated transitions
- Weight and reps input controls
- Set progress indicators
- Rest timer between sets
- Historical records display
- Exercise instructions
- Superset handling with alternate selection

### Saveable State
Custom `Saver` implementations for configuration change survival:

```kotlin
// Weight.kt
data class Weight(val value: Double) {
    companion object {
        val Saver: Saver<Weight, Double> = Saver(
            save = { it.value },
            restore = { Weight(it) }
        )
    }
}

// Usage
val weight = rememberSaveable(stateSaver = Weight.Saver) { mutableStateOf(Weight(0.0)) }
```

## Resources

### Strings (res/values/strings.xml)
- UI labels and text
- Error messages
- Accessibility descriptions

### Changelog (res/values/changelog.xml)
- Version history
- Feature descriptions
- Bug fixes

### Drawables
- `google_icon_g.xml` - Google Sign-In icon

### Fonts
- `roboto_medium.ttf` - Medium weight Roboto font

## Theme

Material 3 theme defined in `compose/util/Theme.kt`:

```kotlin
@Composable
fun RefittedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

## CompositionLocal for Feature Flags

Feature flags exposed via `CompositionLocal`:

```kotlin
val LocalFeatures = compositionLocalOf<ConfigProvider.RemoteConfig> {
    error("No RemoteConfig provided")
}

// Usage
@Composable
fun MyScreen() {
    val features = LocalFeatures.current
    if (features.featureXEnabled) {
        // Show feature X
    }
}
```

## Design Principles

1. **MVVM Pattern**: Clear separation of business logic (ViewModel) and UI (Compose)
2. **Unidirectional Data Flow**: ViewModels emit state, UI reacts
3. **StateFlow**: Reactive state management
4. **Compose Best Practices**: Stateless composables, hoisting state
5. **Saveable State**: Configuration change survival
6. **Hilt Integration**: ViewModels injected via `@HiltViewModel`

## Testing

Currently no UI tests in this module (UI tests are in `:app` as instrumentation tests).

## Usage by :app Module

The `:app` module:
- Provides this module as a dependency
- Hosts the Activity that launches the Compose UI
- Provides repository implementations for ViewModels via Hilt
