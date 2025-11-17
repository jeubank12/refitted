# :util Module

## Purpose

Shared utility functions used across multiple modules. Pure helpers with no business logic.

## Key Responsibilities

- Collection utilities: `groupUntil()`, extensions for `Iterable`
- Logging utilities

## Important Files

- `IterableUtil.kt` - Collection extension functions

## Dependencies

- Pure Kotlin (no module dependencies)

## Testing

```bash
./gradlew :util:test
```

## Used By

- `:dynamo`, `:ui`, `:app` - Various utility functions
