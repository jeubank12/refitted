package com.litus_animae.refitted.data.models

/**
 * A user-facing muscle-group category and the stored exercise-id prefixes it should query.
 * Several categories map to more than one prefix because admin-authored workout programs use
 * inconsistent spellings for the same muscle (e.g. "Quads" vs "Quadricep", "Bicep" vs "Biceps"),
 * and "Back"/"External Rotator" turned out to be the same training focus as "Lats"/"Shoulders"
 * under a different label - confirmed by surveying every admin program's stored exercise ids.
 * "Forearms" and "Lower back" were dropped entirely: no admin program has ever stored an exercise
 * under either prefix.
 */
enum class MuscleGroup(val displayName: String, private val prefixes: List<String>) {
  CHEST("Chest", listOf("Chest")),
  SHOULDERS("Shoulders", listOf("Shoulder", "External Rotator")),
  BICEPS("Biceps", listOf("Bicep", "Biceps")),
  TRICEPS("Triceps", listOf("Tricep")),
  CORE("Core", listOf("Core")),
  TRAPS("Traps", listOf("Traps")),
  LATS("Lats", listOf("Lats", "Back")),
  GLUTES("Glutes", listOf("Glutes")),
  HAMSTRINGS("Hamstrings", listOf("Hamstrings", "Hamstring")),
  QUADS("Quads", listOf("Quads", "Quadricep")),
  CALVES("Calves", listOf("Calf")),
  LEGS("Legs", listOf("Leg")),
  AGILITY("Agility", listOf("Agility", "Rope"));

  companion object {
    // Admin programs use "Compound" as a cross-category label for full-body complexes/circuits
    // rather than a muscle, so every category's query also includes it.
    private const val COMPOUND_PREFIX = "Compound"

    fun displayNames(): List<String> = entries.map { it.displayName }

    /** Stored exercise-id prefixes to query for [displayName], including the shared Compound bucket. */
    fun prefixesFor(displayName: String): List<String> {
      val group = entries.find { it.displayName == displayName }
      return (group?.prefixes ?: listOf(displayName)) + COMPOUND_PREFIX
    }
  }
}
