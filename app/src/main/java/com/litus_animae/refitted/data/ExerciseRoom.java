package com.litus_animae.refitted.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;

@Database(entities = {Exercise.class, ExerciseSet.class}, version = 1)
public abstract class ExerciseRoom extends RoomDatabase {
    public abstract ExerciseDao getExerciseDao();
}
