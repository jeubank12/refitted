package com.litus_animae.refitted.data.models

/**
 * Domain model for simple key-value state persistence.
 */
data class SavedState(
  val key: String,
  val value: String
)
