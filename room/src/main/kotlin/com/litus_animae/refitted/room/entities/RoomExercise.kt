package com.litus_animae.refitted.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.litus_animae.refitted.data.models.Exercise

/**
 * Room entity for Exercise persistence.
 * Domain code should use Exercise from :data instead.
 */
@Entity(
    tableName = "Exercise",
    primaryKeys = ["exercise_name", "exercise_workout"]
)
data class RoomExercise(
    @ColumnInfo(name = "exercise_workout")
    val workout: String,

    @ColumnInfo(name = "exercise_name")
    val id: String,

    val description: String? = null
) {
    /**
     * Convert Room entity to domain model
     */
    fun toDomain(): Exercise = Exercise(
        workout = workout,
        id = id,
        description = description
    )

    companion object {
        /**
         * Create Room entity from domain model
         */
        fun fromDomain(exercise: Exercise): RoomExercise = RoomExercise(
            workout = exercise.workout,
            id = exercise.id,
            description = exercise.description
        )
    }
}
