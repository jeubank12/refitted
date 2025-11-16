package com.litus_animae.refitted.models

import java.time.Instant

data class Record(
  val weight: Double,
  val reps: Int,
  val set: ExerciseSet,
  val completed: Instant,
  val cumulativeReps: Int = 0,
  val stored: Boolean = false
)
