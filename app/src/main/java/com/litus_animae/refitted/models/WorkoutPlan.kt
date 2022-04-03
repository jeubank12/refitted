package com.litus_animae.refitted.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "workouts")
data class WorkoutPlan(
    @PrimaryKey
    val workout: String,
    val totalDays: Int = 84,
    val lastViewedDay: Int = 1,
    val workoutStartDate: Date = Date(0L),
    val restDays: List<Int> = emptyList()
)

data class DayAndWorkout(val day: String, val workoutId: String)
