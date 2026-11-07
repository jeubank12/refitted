package com.litus_animae.refitted.room.entities

import androidx.room.Entity
import java.time.Instant

/**
 * When [day] of [workout] last had a set logged. Kept apart from SetRecord because a record's
 * `target_set` pins the day number it was logged under, while a day's number can change (deleting
 * an earlier day of a custom plan renumbers the rest) - records are history and never move, this
 * is the part that does.
 */
@Entity(
    tableName = "DayCompletion",
    primaryKeys = ["workout", "day"]
)
data class RoomDayCompletion(
    val workout: String,
    val day: Int,
    val completed: Instant
)
