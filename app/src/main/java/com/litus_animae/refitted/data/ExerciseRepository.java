package com.litus_animae.refitted.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.SetRecord;
import com.litus_animae.refitted.threads.GetExerciseRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseRepository {

    private Context applicationContext;
    private ExecutorService threadPoolService;
    private MutableLiveData<List<ExerciseSet>> exercises = new MutableLiveData<>();
    private MutableLiveData<List<ExerciseRecord>> records = new MutableLiveData<>();

    public ExerciseRepository(Context context) {
        applicationContext = context.getApplicationContext();
        threadPoolService = Executors.newCachedThreadPool();
    }

    private static List<ExerciseRecord> GenerateRecords(List<ExerciseSet> ex) {
        ArrayList<ExerciseRecord> records = new ArrayList<>(ex.size());
        for (ExerciseSet e : ex) {
            records.add(new ExerciseRecord(e));
        }
        return records;
    }

    public void LoadExercises(String day, String workoutId) {
        threadPoolService.submit(new GetExerciseRunnable(applicationContext, exercises, records,
                day, workoutId));
    }

    public void StoreSetRecord(SetRecord record) {

    }

    public LiveData<List<ExerciseSet>> getExercises() {
        return exercises;
    }

    public LiveData<List<ExerciseRecord>> getRecords() {
        return records;
    }
}
