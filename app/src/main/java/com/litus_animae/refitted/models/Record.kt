package com.litus_animae.refitted.models

data class Record(
  val weight: Double,
  val reps: Int,
  val set: ExerciseSet,
  val cumulativeReps: Int = 0,
  val stored: Boolean = false
)
