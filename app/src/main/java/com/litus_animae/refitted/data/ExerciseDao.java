package com.litus_animae.refitted.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.SetRecord;

import java.util.Date;
import java.util.List;

@Dao
public interface ExerciseDao {
    @Query("select distinct step from exerciseset where day = :day and workout = :workout")
    LiveData<List<String>> getSteps(String day, String workout);

    @Query("select * from exerciseset where day = :day and workout = :workout and step = :step")
    ExerciseSet getExerciseSet(String day, String workout, String step);

    @Query("select * from exerciseset where day = :day and workout = :workout and step in (:steps) order by step")
    LiveData<List<ExerciseSet>> getExerciseSets(String day, String workout, String... steps);

    @Query("select * from exercise where exercise_name = :name and exercise_workout = :workout")
    Exercise getExercise(String name, String workout);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void storeExercise(Exercise exercise);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void storeExerciseSet(ExerciseSet exerciseSet);

    @Query("select * from setrecord where completed > :minDate and exercise = :targetExercise")
    List<SetRecord> getSetRecords(Date minDate, String targetExercise);

    @Query("select * from setrecord where exercise = :targetExercise order by completed desc")
    SetRecord getLatestSetRecord(String targetExercise);

    @Query("select * from setrecord where exercise = :targetExercise order by completed desc")
    List<SetRecord> getAllSetRecord(String targetExercise);

    @Insert
    void storeExerciseRecord(SetRecord exerciseRecord);
}
