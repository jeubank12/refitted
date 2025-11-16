package com.litus_animae.refitted.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.litus_animae.refitted.room.entities.RoomSavedState
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: RoomSavedState)

    @Query("SELECT * FROM `SavedState`")
    fun allState(): Flow<List<RoomSavedState>>

    @Query("SELECT * FROM `SavedState` where `key` = :key")
    fun getState(key: String): Flow<RoomSavedState?>

    @Query("SELECT * FROM `SavedState` where `key` = :key")
    suspend fun loadState(key: String): RoomSavedState?

    @Query("DELETE FROM `SavedState` WHERE `key` = :key")
    suspend fun clear(key: String)
}