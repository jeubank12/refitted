# :util Module

## Purpose

The `:util` module provides **shared utility functions** used across multiple modules. It contains reusable helpers, extensions, and common functionality with no business logic.

## Module Type

**Pure Kotlin Library** - No Android dependencies (currently, though it's technically an Android Library)

## Key Responsibilities

1. **Collection Utilities**: Extensions for Iterable operations
2. **Logging**: Centralized logging utilities
3. **General Helpers**: Reusable functions for common tasks

## Dependencies

```gradle
dependencies {
    // Kotlin stdlib only
    // JUnit 5 for testing
}
```

**No dependencies on other app modules** - This is a utility foundation.

## Structure

```
util/src/main/kotlin/com/litus_animae/refitted/util/
├── IterableUtil.kt              # Collection extension functions
├── LogUtil.kt                   # Logging helpers
└── [other utilities]
```

## Utilities

### IterableUtil
Collection extensions for common operations:

```kotlin
/**
 * Groups iterable into chunks until predicate is true
 */
fun <T> Iterable<T>.groupUntil(predicate: (T) -> Boolean): List<List<T>>

/**
 * Maps elements with index
 */
fun <T, R> Iterable<T>.mapIndexed(transform: (Int, T) -> R): List<R>

/**
 * Safely gets element at index or null
 */
fun <T> List<T>.getOrNull(index: Int): T?
```

### LogUtil
Centralized logging:

```kotlin
object LogUtil {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
}
```

## Testing

Tests verify utility function behavior:
- `IterableUtilTest` - Collection extension tests

Run tests:
```bash
./gradlew :util:test
```

## Design Principles

1. **Pure Functions**: Stateless utility functions
2. **Extension Functions**: Kotlin-style extensions for readability
3. **No Business Logic**: Only generic helpers
4. **Well-Tested**: High test coverage for reliability
5. **Single Responsibility**: Each utility file has one clear purpose

## Usage by Other Modules

- `:data` - May use collection utilities
- `:room` - May use logging
- `:dynamo` - May use logging and collection utilities
- `:ui` - May use collection utilities
- `:app` - Uses utilities throughout

## Future Additions

Potential utilities to add:
- Date/time formatting helpers
- String manipulation extensions
- Number formatting utilities
- Validation helpers
