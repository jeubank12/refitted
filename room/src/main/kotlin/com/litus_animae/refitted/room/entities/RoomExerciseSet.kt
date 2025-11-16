package com.litus_animae.refitted.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room entity for ExerciseSet persistence.
 * Domain code should use the corresponding model from :data instead - domain code uses ExerciseSet from :data.
 */
@Entity(
    tableName = "exerciseset",
    primaryKeys = ["day", "step", "workout"],
    foreignKeys = [ForeignKey(
        entity = RoomExercise::class,
        parentColumns = ["exercise_name", "exercise_workout"],
        childColumns = ["name", "workout"]
    )],
    indices = [Index(value = ["name", "workout"])]
)
data class RoomExerciseSet(
    val workout: String,
    val day: String,
    val step: String,
    val primaryStep: Int,
    val superSetStep: Int?,
    val alternateStep: String?,
    val name: String,
    val note: String,
    val reps: Int,
    val sets: Int,
    @ColumnInfo(name = "toFailure")
    val isToFailure: Boolean,
    val rest: Int,
    val repsUnit: String,
    val repsRange: Int,
    val timeLimit: Int?,
    val timeLimitUnit: String?,
    val repsSequence: List<Int>
) {
    companion object {
        /**
         * Parse alternate step identifier from full step (e.g., "1.2.a" -> "a")
         */
        fun parseAlternateStep(input: String): String? =
            """^(\d+\.)+([a-z]+)""".toRegex().find(input)?.groupValues?.lastOrNull()

        /**
         * Parse superset step number from full step (e.g., "1.2.3" -> 3)
         */
        fun parseSuperSetStep(input: String): Int? =
            """^(\d+\.){2,}(\d+)""".toRegex().find(input)?.groupValues?.lastOrNull()?.toIntOrNull()

        /**
         * Parse primary step number from full step (e.g., "1.2" -> 2)
         */
        fun parsePrimaryStep(input: String): Int =
            """^\d+\.(\d+)""".toRegex().find(input)?.groupValues?.lastOrNull()?.toIntOrNull() ?: 0
    }
}
