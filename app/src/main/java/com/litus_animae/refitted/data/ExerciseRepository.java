package com.litus_animae.refitted.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.SetRecord;
import com.litus_animae.refitted.threads.CloseDatabaseRunnable;
import com.litus_animae.refitted.threads.StoreRecordsRunnable;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseRepository {

    private static final String TAG = "ExerciseRepository";

    private WeakReference<Context> applicationContext;
    private ExecutorService threadPoolService;
    private MediatorLiveData<List<ExerciseSet>> exercises = new MediatorLiveData<>();
    private LiveData<List<ExerciseSet>> currentExerciseSource = null;
    private LiveData<List<ExerciseRecord>> records;

    public ExerciseRepository(Context context) {
        applicationContext = new WeakReference<>(context.getApplicationContext());
        threadPoolService = Executors.newCachedThreadPool();

        LiveData<ExerciseRoom> room = RoomDataService.getExerciseRoomAsync(applicationContext);
        records = Transformations.switchMap(room, roomDb -> {
            if (roomDb != null) {
                return Transformations.map(exercises, loadedExercises -> {
                    Date tonightMidnight = Date.from(LocalDateTime.now().toLocalDate().atStartOfDay().toInstant(ZoneOffset.ofHours(0)));
                    ArrayList<ExerciseRecord> recordObjects = new ArrayList<>();
                    for (ExerciseSet e : loadedExercises) {
                        ExerciseRecord record = new ExerciseRecord(e);
                        try {
                            record.setLatestSet(roomDb.getExerciseDao().getLatestSetRecord(e.getExerciseName()));
                            record.setSets(roomDb.getExerciseDao()
                                    .getSetRecords(tonightMidnight, e.getExerciseName()));
                            record.setAllSets(roomDb.getExerciseDao().getAllSetRecord(e.getExerciseName()));
                        } catch (Exception ex) {
                            Log.e(TAG, "loadExercises: failed retrieving records", ex);
                        }
                        recordObjects.add(record);
                    }
                    return recordObjects;
                });
            }
            MutableLiveData<List<ExerciseRecord>> emptyResult = new MutableLiveData<>();
            emptyResult.setValue(new ArrayList<>());
            return emptyResult;
        });
    }

    public void loadExercises(String day, String workoutId) {
        LiveData<ExerciseRoom> room = RoomDataService.getExerciseRoomAsync(applicationContext);
        if (currentExerciseSource != null) {
            exercises.removeSource(currentExerciseSource);
        }
        currentExerciseSource = Transformations.switchMap(room, roomDb -> {
            if (roomDb != null) {
                return Transformations.switchMap(
                        Transformations.map(
                                roomDb.getExerciseDao().getSteps(day, workoutId),
                                HashSet::new),
                        stepKeys -> roomDb.getExerciseDao().getExerciseSets(day, workoutId, stepKeys.toArray(new String[0]))
                );
            }
            return new MutableLiveData<>();
        });
        exercises.addSource(currentExerciseSource, exerciseSets -> {
            for (ExerciseSet set : exerciseSets) {
                set.setExercise(Transformations.switchMap(room, roomDb -> {
                    if (roomDb != null) {
                        return roomDb.getExerciseDao().getExercise(set.getName(), set.getWorkout());
                    }
                    return new MutableLiveData<>();
                }));
            }
            exercises.setValue(exerciseSets);
        });
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

    public void shutdown() {
        threadPoolService.submit(new CloseDatabaseRunnable(applicationContext.get()));
        threadPoolService.shutdown();
    }
}
