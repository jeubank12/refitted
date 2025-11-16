package com.litus_animae.refitted.room

import androidx.paging.PagingSource
import androidx.room.*
import com.litus_animae.refitted.room.entities.RoomWorkoutPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<RoomWorkoutPlan>)

    @Update
    fun update(workoutPlan: RoomWorkoutPlan)

    @Query("SELECT * FROM workouts")
    fun pagingSource(): PagingSource<Int, RoomWorkoutPlan>

    @Query("SELECT * FROM workouts")
    suspend fun getAll(): List<RoomWorkoutPlan>

    @Query("SELECT * FROM workouts where `workout` = :name")
    fun planByName(name: String): Flow<RoomWorkoutPlan?>

    @Query("DELETE FROM workouts")
    suspend fun clearAll()
}