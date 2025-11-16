package com.litus_animae.refitted.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "workouts")
data class WorkoutPlan(
  @PrimaryKey
  val workout: String,
  val totalDays: Int = 84,
  val lastViewedDay: Int = 1,
  val workoutStartDate: Instant = Instant.ofEpochMilli(0),
  val restDays: List<Int> = emptyList(),
  val description: String = "",
  val globalAlternateLabels: List<String> = emptyList(),
  val globalAlternate: Int? = null
)

data class DayAndWorkout(val day: String, val workoutId: String)
