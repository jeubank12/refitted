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

    /** Selectable programs only - equipment libraries (kind != PROGRAM) never appear in the drawer. */
    @Query("SELECT * FROM workouts where `kind` = 'PROGRAM' ORDER BY isCustom ASC, workout ASC")
    fun pagingSource(): PagingSource<Int, RoomWorkoutPlan>

    @Query("SELECT * FROM workouts")
    suspend fun getAll(): List<RoomWorkoutPlan>

    @Query("SELECT * FROM workouts where `workout` = :name")
    fun planByName(name: String): Flow<RoomWorkoutPlan?>

    @Query("SELECT * FROM workouts where `workout` = :name")
    suspend fun getByName(name: String): RoomWorkoutPlan?

    /**
     * Admin-authored plans the user has synced access to, program or equipment library alike -
     * the plan list itself is synced in full on refresh (unlike per-day exercise content), so this
     * doesn't require an extra network round-trip.
     */
    @Query("SELECT * FROM workouts where `isCustom` = 0 ORDER BY workout ASC")
    fun getServerPlans(): Flow<List<RoomWorkoutPlan>>

    @Query("DELETE FROM workouts")
    suspend fun clearAll()

    /**
     * Clears only admin-authored plans, leaving user-created (`isCustom`) plans untouched -
     * used before re-inserting the server's plan list on refresh.
     */
    @Query("DELETE FROM workouts where `isCustom` = 0")
    suspend fun clearServerPlans()
}