package com.litus_animae.refitted.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.litus_animae.refitted.data.models.SavedState

/**
 * Room entity for SavedState persistence.
 * Internal to the room module - domain code uses SavedState from :data.
 */
@Entity(tableName = "SavedState")
internal data class RoomSavedState(
    @PrimaryKey
    val key: String,
    val value: String
) {
    /**
     * Convert Room entity to domain model
     */
    fun toDomain(): SavedState = SavedState(
        key = key,
        value = value
    )

    companion object {
        /**
         * Create Room entity from domain model
         */
        fun fromDomain(state: SavedState): RoomSavedState = RoomSavedState(
            key = state.key,
            value = state.value
        )
    }
}
