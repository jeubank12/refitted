package com.litus_animae.refitted.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.SetRecord;
import com.litus_animae.refitted.threads.CloseDatabaseRunnable;
import com.litus_animae.refitted.threads.GetExerciseRunnable;
import com.litus_animae.refitted.threads.StoreRecordsRunnable;

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

    public void loadExercises(String day, String workoutId) {
        threadPoolService.submit(new GetExerciseRunnable(applicationContext, exercises, records,
                day, workoutId));
    }

    public void storeSetRecord(SetRecord record) {
        threadPoolService.submit(new StoreRecordsRunnable(applicationContext, record));
    }

    public LiveData<List<ExerciseSet>> getExercises() {
        return exercises;
    }

    public LiveData<List<ExerciseRecord>> getRecords() {
        return records;
    }

    public void shutdown(){
        threadPoolService.submit(new CloseDatabaseRunnable(applicationContext));
        threadPoolService.shutdown();
    }
}
