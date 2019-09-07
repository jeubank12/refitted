package com.litus_animae.refitted.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.room.Room;

import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.SetRecord;
import com.litus_animae.refitted.threads.CloseDatabaseRunnable;
import com.litus_animae.refitted.threads.GetExerciseRunnable;
import com.litus_animae.refitted.threads.StoreRecordsRunnable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseRepository {

    private WeakReference<Context> applicationContext;
    private ExecutorService threadPoolService;
    private MediatorLiveData<List<ExerciseSet>> exercises = new MediatorLiveData<>();
    private MutableLiveData<List<ExerciseRecord>> records = new MutableLiveData<>();

    public ExerciseRepository(Context context) {
        applicationContext = new WeakReference<Context>(context.getApplicationContext());
        threadPoolService = Executors.newCachedThreadPool();
    }

    public void loadExercises(String day, String workoutId) {
        LiveData<ExerciseRoom> room = RoomDataService.getExerciseRoomAsync(applicationContext);
        LiveData<Set<String>> keys = Transformations.switchMap(room, roomDb -> {
            if (roomDb != null){
                return Transformations.map(
                        roomDb.getExerciseDao().getSteps(day, workoutId),
                        HashSet::new);
            }
            return new MutableLiveData<>();
        });
        exercises.addSource(Transformations.switchMap(keys, stepKeys -> {
            if (stepKeys != null){
                return Transformations.switchMap(room, roomDb -> {
                    if (roomDb != null) {
                        return roomDb.getExerciseDao().getExerciseSets(day, workoutId, stepKeys.toArray(new String[0]));
                    }
                    return new MutableLiveData<>();
                });
            }
            return new MutableLiveData<>();
        }), loadedExercises -> exercises.setValue(loadedExercises));
//        threadPoolService.submit(new GetExerciseRunnable(applicationContext.get(), exercises, records,
//                day, workoutId));
    }

    public void storeSetRecord(SetRecord record) {
        threadPoolService.submit(new StoreRecordsRunnable(applicationContext.get(), record));
    }

    public LiveData<List<ExerciseSet>> getExercises() {
        return exercises;
    }

    public LiveData<List<ExerciseRecord>> getRecords() {
        return records;
    }

    public void shutdown(){
        threadPoolService.submit(new CloseDatabaseRunnable(applicationContext.get()));
        threadPoolService.shutdown();
    }
}
