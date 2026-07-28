package com.litus_animae.refitted.data.models

/**
 * Domain model representing an exercise.
 * Pure domain object with no persistence or serialization concerns.
 *
 * [id] encodes "{muscleGroup}_{name}" - the muscle group prefix isn't a separate stored field,
 * it's parsed out of the id (see [muscleGroup]). This lets exercises be queried by muscle group
 * via a begins_with/LIKE prefix match without a schema change.
 */
data class Exercise(
  val workout: String,
  val id: String,
  val description: String? = null
) {
  /**
   * Extracts the exercise name from the ID.
   * ID format is typically "{muscleGroup}_{name}"
   */
  val name: String?
    get() = if (id.isEmpty() || !id.contains("_")) {
      null
    } else {
      id.split("_", limit = 2)[1]
    }

  /**
   * Extracts the muscle group from the ID - the prefix before the first "_".
   */
  val muscleGroup: String?
    get() = if (id.isEmpty() || !id.contains("_")) {
      null
    } else {
      id.split("_", limit = 2)[0]
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
