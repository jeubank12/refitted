package com.litus_animae.refitted.data.room

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.*
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import com.litus_animae.refitted.models.SetRecord
import java.util.*

@Dao
interface ExerciseDao {
    @Query("select distinct step from exerciseset where day = :day and workout = :workout")
    fun getSteps(day: String, workout: String): LiveData<List<String>>

    @Query("select * from exerciseset where day = :day and workout = :workout and step = :step")
    fun getExerciseSet(day: String, workout: String, step: String): RoomExerciseSet?

    @Query("select * from exerciseset where day = :day and workout = :workout and step in (:steps) order by step")
    fun getExerciseSets(day: String, workout: String, vararg steps: String): LiveData<List<RoomExerciseSet>>

    @Query("select * from exercise where exercise_name = :name and exercise_workout = :workout")
    fun getExercise(name: String, workout: String): LiveData<Exercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun storeExercise(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun storeExerciseSet(exerciseSet: RoomExerciseSet)

    @Transaction
    suspend fun storeExerciseAndSet(exercise: Exercise, exerciseSet: RoomExerciseSet){
        storeExercise(exercise)
        storeExerciseSet(exerciseSet)
    }

    @Query("select * from setrecord where completed > :minDate and exercise = :targetExercise")
    fun getSetRecords(minDate: Date, targetExercise: String): LiveData<List<SetRecord>>

    @Query("select * from setrecord where exercise = :targetExercise order by completed desc")
    fun getLatestSetRecord(targetExercise: String): LiveData<SetRecord?>

    @Query("select * from setrecord where exercise = :targetExercise order by completed desc")
    fun getAllSetRecord(targetExercise: String): DataSource.Factory<Int, SetRecord>

    @Insert
    suspend fun storeExerciseRecord(exerciseRecord: SetRecord)

    @Query("select max(completed) as latest_completion, target_set from setrecord " +
            "where workout = :workout group by target_set")
    fun getDayCompletedSets(workout: String): LiveData<List<ExerciseCompletionRecord>>

    data class ExerciseCompletionRecord(@ColumnInfo(name = "latest_completion") val latestCompletion: Date,
                                        @ColumnInfo(name = "target_set") val dayAndSet: String) {
        @Ignore
        val day = dayAndSet.split("\\.".toRegex(), 2).toTypedArray().getOrElse(0) { "" }
    }
}