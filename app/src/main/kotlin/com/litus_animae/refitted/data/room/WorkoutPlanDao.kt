package com.litus_animae.refitted.data.room

import androidx.paging.PagingSource
import androidx.room.*
import com.litus_animae.refitted.models.WorkoutPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<WorkoutPlan>)

    @Update
    fun update(workoutPlan: WorkoutPlan)

    @Query("SELECT * FROM workouts")
    fun pagingSource(): PagingSource<Int, WorkoutPlan>

    @Query("SELECT * FROM workouts")
    suspend fun getAll(): List<WorkoutPlan>

    @Query("SELECT * FROM workouts where `workout` = :name")
    fun planByName(name: String): Flow<WorkoutPlan?>

    @Query("DELETE FROM workouts")
    suspend fun clearAll()
}