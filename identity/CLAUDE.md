# :identity Module

## Purpose

Firebase integration for authentication, remote configuration, and crash reporting. Abstracts Firebase from the rest of the app.

## Key Responsibilities

- Firebase Auth: Google Sign-In, anonymous authentication
- Remote Config: Feature flags and configuration
- Crashlytics: Error tracking
- Provider abstractions for testing

## Important Files

- `AuthProvider.kt` - Firebase Auth abstraction interface
- `ConfigProvider.kt` - Remote Config abstraction interface
- `src/main/res/xml/remote_config_defaults.xml` - Default feature flag values

## Dependencies

- Firebase BOM with Auth, Config, Analytics, Crashlytics, Firestore
- Google Play Services Auth

## Testing

Mock implementations can be provided for testing (e.g., `MockAuthProvider`)

## Used By

- `:ui` - UserViewModel uses AuthProvider, ConfigProvider for feature flags
- `:app` - Provides implementations via Hilt
