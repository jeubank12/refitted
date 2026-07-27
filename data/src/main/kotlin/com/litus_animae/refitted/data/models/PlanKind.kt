package com.litus_animae.refitted.data.models

/** Declaration order is picker order: locations, then equipment, then programs. */
enum class PlanKind {
  LOCATION, EQUIPMENT, PROGRAM;

  companion object {
    /** Unknown or absent stored values fall back to PROGRAM - every pre-existing plan is one. */
    fun fromStored(value: String?): PlanKind =
      entries.find { it.name.equals(value, ignoreCase = true) } ?: PROGRAM
  }
}
