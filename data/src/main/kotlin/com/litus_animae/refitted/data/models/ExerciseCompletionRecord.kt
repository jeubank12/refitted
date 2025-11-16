package com.litus_animae.refitted.data.models

import java.time.Instant

/**
 * Domain model representing when an exercise/set was last completed.
 */
data class ExerciseCompletionRecord(
    val latestCompletion: Instant,
    val dayAndSet: String
) {
    /**
     * Extract day from the dayAndSet identifier (format: "{day}.{set}")
     */
    val day: String = dayAndSet.split(".", limit = 2).getOrElse(0) { "" }
}
