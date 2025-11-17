# :identity Module

## Purpose

The `:identity` module handles **Firebase integration** for authentication, remote configuration, and crash reporting. It abstracts Firebase concerns from the rest of the app.

## Module Type

**Android Library** - Requires Android framework for Firebase

## Key Responsibilities

1. **Authentication**: Google Sign-In and anonymous authentication via Firebase Auth
2. **Remote Config**: Feature flags and configuration via Firebase Remote Config
3. **Crashlytics**: Error tracking and crash reporting
4. **Provider Abstractions**: Interfaces for testing and flexibility

## Dependencies

```gradle
dependencies {
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.firestore)

    // Google Sign-In
    implementation(libs.play.services.auth)
}
```

**No dependencies on other app modules** - This is a cross-cutting concern.

## Structure

```
identity/src/main/kotlin/com/litus_animae/refitted/identity/
├── AuthProvider.kt              # Firebase Auth abstraction
├── ConfigProvider.kt            # Remote Config abstraction
└── CrashlyticsProvider.kt       # Crashlytics abstraction (if exists)
```

## Provider Interfaces and Implementations

### AuthProvider
Abstracts Firebase Authentication:

```kotlin
interface AuthProvider {
    // Get current Firebase Auth instance
    fun auth(): FirebaseAuth

    // Get current user
    fun currentUser(): FirebaseUser?

    // Sign in with Google credential
    suspend fun signInWithGoogle(idToken: String): AuthResult

    // Sign in anonymously
    suspend fun signInAnonymously(): AuthResult

    // Sign out
    fun signOut()

    // Observe auth state changes
    fun authStateFlow(): Flow<FirebaseUser?>
}

class AuthProviderLive @Inject constructor() : AuthProvider {
    override fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    override fun currentUser(): FirebaseUser? = auth().currentUser

    override suspend fun signInWithGoogle(idToken: String): AuthResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth().signInWithCredential(credential).await()
    }

    override suspend fun signInAnonymously(): AuthResult {
        return auth().signInAnonymously().await()
    }

    override fun signOut() {
        auth().signOut()
    }

    override fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth().addAuthStateListener(listener)
        awaitClose { auth().removeAuthStateListener(listener) }
    }
}
```

### ConfigProvider
Abstracts Firebase Remote Config:

```kotlin
interface ConfigProvider {
    // Get Remote Config instance
    fun remoteConfig(): FirebaseRemoteConfig

    // Fetch and activate config
    suspend fun fetchAndActivate(): Boolean

    // Get boolean flag
    fun getBoolean(key: String): Boolean

    // Get string value
    fun getString(key: String): String

    // Get long value
    fun getLong(key: String): Long

    // Feature flags
    data class RemoteConfig(
        val featureXEnabled: Boolean,
        val featureYEnabled: Boolean,
        // ... other flags
    )

    // Get all feature flags as object
    fun getFeatureFlags(): RemoteConfig
}

class ConfigProviderLive @Inject constructor() : ConfigProvider {
    private val config = FirebaseRemoteConfig.getInstance().apply {
        setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600  // 1 hour
            }
        )
        setDefaultsAsync(R.xml.remote_config_defaults)
    }

    override fun remoteConfig(): FirebaseRemoteConfig = config

    override suspend fun fetchAndActivate(): Boolean {
        return config.fetchAndActivate().await()
    }

    override fun getBoolean(key: String): Boolean = config.getBoolean(key)

    override fun getString(key: String): String = config.getString(key)

    override fun getLong(key: String): Long = config.getLong(key)

    override fun getFeatureFlags(): RemoteConfig {
        return RemoteConfig(
            featureXEnabled = getBoolean("feature_x_enabled"),
            featureYEnabled = getBoolean("feature_y_enabled")
        )
    }
}
```

### CrashlyticsProvider (if exists)
Abstracts Firebase Crashlytics:

```kotlin
interface CrashlyticsProvider {
    fun log(message: String)
    fun recordException(exception: Throwable)
    fun setUserId(userId: String)
}
```

## Remote Config Defaults

Default values defined in `identity/src/main/res/xml/remote_config_defaults.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<defaultsMap>
    <entry>
        <key>feature_x_enabled</key>
        <value>false</value>
    </entry>
    <!-- ... other defaults -->
</defaultsMap>
```

## Design Principles

1. **Interface-Based**: Provider interfaces allow testing with mocks
2. **Firebase Isolation**: Firebase dependencies contained in this module
3. **Async-First**: Suspend functions for async operations
4. **Flow-Based**: Observable auth state via Kotlin Flow
5. **Configuration**: Remote Config for runtime feature flags

## Usage by Other Modules

- `:ui` - UserViewModel uses AuthProvider for sign-in/sign-out
- `:ui` - Compose UI accesses ConfigProvider for feature flags via CompositionLocal
- `:app` - Provides implementations via Hilt DI

## Hilt Configuration

Provided by `:app` module via IdentityModule:

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

## Testing

For testing, mock implementations can be provided:

```kotlin
class MockAuthProvider : AuthProvider {
    override fun auth(): FirebaseAuth = mockFirebaseAuth
    // ... mock implementations
}
```
