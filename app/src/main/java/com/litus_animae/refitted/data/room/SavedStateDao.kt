package com.litus_animae.refitted.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.litus_animae.refitted.models.SavedState
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SavedState)

    @Query("SELECT * FROM `SavedState`")
    fun allState(): Flow<List<SavedState>>

    @Query("SELECT * FROM `SavedState` where `key` = :key limit 1")
    fun getState(key: String): Flow<SavedState?>

    @Query("DELETE FROM `SavedState` WHERE `key` = :key")
    suspend fun clear(key: String)
}