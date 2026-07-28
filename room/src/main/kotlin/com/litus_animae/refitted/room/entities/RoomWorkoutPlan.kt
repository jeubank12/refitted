package com.litus_animae.refitted.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.litus_animae.refitted.data.models.WorkoutPlan
import java.time.Instant

/**
 * Room entity for WorkoutPlan persistence.
 * Domain code should use the corresponding model from :data instead - domain code uses WorkoutPlan from :data.
 */
@Entity(tableName = "workouts")
data class RoomWorkoutPlan(
    @PrimaryKey
    val workout: String,
    val totalDays: Int = 84,
    val lastViewedDay: Int = 1,
    val workoutStartDate: Instant = Instant.ofEpochMilli(0),
    val restDays: List<Int> = emptyList(),
    val description: String = "",
    val globalAlternateLabels: List<String> = emptyList(),
    val globalAlternate: Int? = null,
    val isCustom: Boolean = false
) {
    /**
     * Convert Room entity to domain model
     */
    fun toDomain(): WorkoutPlan = WorkoutPlan(
        workout = workout,
        totalDays = totalDays,
        lastViewedDay = lastViewedDay,
        workoutStartDate = workoutStartDate,
        restDays = restDays,
        description = description,
        globalAlternateLabels = globalAlternateLabels,
        globalAlternate = globalAlternate,
        isCustom = isCustom
    )

    companion object {
        /**
         * Create Room entity from domain model
         */
        fun fromDomain(workoutPlan: WorkoutPlan): RoomWorkoutPlan = RoomWorkoutPlan(
            workout = workoutPlan.workout,
            totalDays = workoutPlan.totalDays,
            lastViewedDay = workoutPlan.lastViewedDay,
            workoutStartDate = workoutPlan.workoutStartDate,
            restDays = workoutPlan.restDays,
            description = workoutPlan.description,
            globalAlternateLabels = workoutPlan.globalAlternateLabels,
            globalAlternate = workoutPlan.globalAlternate,
            isCustom = workoutPlan.isCustom
        )
    }
}
