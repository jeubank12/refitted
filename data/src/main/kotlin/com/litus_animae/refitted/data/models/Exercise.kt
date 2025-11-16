package com.litus_animae.refitted.data.models

/**
 * Domain model representing an exercise.
 * Pure domain object with no persistence or serialization concerns.
 */
data class Exercise(
    val workout: String,
    val id: String,
    val description: String? = null
) {
    /**
     * Extracts the exercise name from the ID.
     * ID format is typically "{prefix}_{name}"
     */
    val name: String?
        get() = if (id.isEmpty() || !id.contains("_")) {
            null
        } else {
            id.split("_", limit = 2)[1]
        }

    /**
     * Gets the exercise name, with option to return null or empty string.
     */
    fun getName(allowNull: Boolean): String? {
        if (allowNull) {
            return name
        }
        return if (id.isEmpty() || !id.contains("_")) "" else id.split("_", limit = 2)[1]
    }
}
