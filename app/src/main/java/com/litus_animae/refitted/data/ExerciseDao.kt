package com.litus_animae.refitted.data

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord
import java.util.*

@Dao
interface ExerciseDao {
    @Query("select distinct step from exerciseset where day = :day and workout = :workout")
    fun getSteps(day: String, workout: String): LiveData<List<String>>

    @Query("select * from exerciseset where day = :day and workout = :workout and step = :step")
    fun getExerciseSet(day: String, workout: String, step: String): ExerciseSet?

    @Query("select * from exerciseset where day = :day and workout = :workout and step in (:steps) order by step")
    fun getExerciseSets(day: String, workout: String, vararg steps: String?): LiveData<List<ExerciseSet>>

    @Query("select * from exercise where exercise_name = :name and exercise_workout = :workout")
    fun getExercise(name: String, workout: String): LiveData<Exercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun storeExercise(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun storeExerciseSet(exerciseSet: ExerciseSet)

    @Query("select * from setrecord where completed > :minDate and exercise = :targetExercise")
    fun getSetRecords(minDate: Date, targetExercise: String): LiveData<List<SetRecord>>

    @Query("select * from setrecord where exercise = :targetExercise order by completed desc")
    fun getLatestSetRecord(targetExercise: String): LiveData<SetRecord?>

    @Query("select * from setrecord where exercise = :targetExercise order by completed desc")
    fun getAllSetRecord(targetExercise: String): DataSource.Factory<Int, SetRecord?>

    @Insert
    fun storeExerciseRecord(exerciseRecord: SetRecord)
}