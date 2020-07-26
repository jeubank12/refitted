package com.litus_animae.refitted.data;

import androidx.lifecycle.LiveData;

import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.SetRecord;

import java.util.List;

public interface ExerciseRepository {
    void loadExercises(String day, String workoutId);

    void storeSetRecord(SetRecord record);

    LiveData<List<ExerciseSet>> getExercises();

    LiveData<List<ExerciseRecord>> getRecords();

    void shutdown();
}
