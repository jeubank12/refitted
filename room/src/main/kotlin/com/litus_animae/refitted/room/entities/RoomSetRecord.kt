package com.litus_animae.refitted.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.litus_animae.refitted.data.models.SetRecord
import java.time.Instant

/**
 * Room entity for SetRecord persistence.
 * Internal to the room module - domain code uses SetRecord from :data.
 */
@Entity(
    tableName = "SetRecord",
    primaryKeys = ["exercise", "completed"]
)
internal data class RoomSetRecord(
    val weight: Double,
    val reps: Int,
    val workout: String,
    @ColumnInfo(name = "target_set")
    val targetSet: String,
    val completed: Instant,
    val exercise: String
) {
    /**
     * Convert Room entity to domain model
     */
    fun toDomain(): SetRecord = SetRecord(
        weight = weight,
        reps = reps,
        workout = workout,
        targetSet = targetSet,
        completed = completed,
        exercise = exercise
    )

    companion object {
        /**
         * Create Room entity from domain model
         */
        fun fromDomain(record: SetRecord): RoomSetRecord = RoomSetRecord(
            weight = record.weight,
            reps = record.reps,
            workout = record.workout,
            targetSet = record.targetSet,
            completed = record.completed,
            exercise = record.exercise
        )
    }
}
