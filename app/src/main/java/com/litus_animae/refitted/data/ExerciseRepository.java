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
    // TODO remove this and only access sets via records
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
                    if (loadedExercises == null) {
                        return new ArrayList<>();
                    }
                    Log.i(TAG, "ExerciseRepository: detected " + loadedExercises.size() + " new exercises, loading records");
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
                    Log.i(TAG, "ExerciseRepository: records loaded");
                    return recordObjects;
                });
            }
            Log.i(TAG, "ExerciseRepository: Room not yet initialized, returning empty list");
            MutableLiveData<List<ExerciseRecord>> emptyResult = new MutableLiveData<>();
            emptyResult.setValue(new ArrayList<>());
            return emptyResult;
        });
    }

    public void loadExercises(String day, String workoutId) {
        LiveData<ExerciseRoom> room = RoomDataService.getExerciseRoomAsync(applicationContext);
        if (currentExerciseSource != null) {
            Log.d(TAG, "loadExercises: removing previously loaded exercises");
            exercises.removeSource(currentExerciseSource);
        }
        currentExerciseSource = Transformations.switchMap(room, roomDb -> {
            if (roomDb != null) {
                DynamoDataService dynamoService = new DynamoDataService(applicationContext.get(), roomDb);
                dynamoService.execute(day, workoutId);
                return Transformations.switchMap(
                        Transformations.map(
                                roomDb.getExerciseDao().getSteps(day, workoutId),
                                HashSet::new),
                        stepKeys -> {
                            Log.i(TAG, "loadExercises: found exercise set numbers, loading sets");
                            return roomDb.getExerciseDao().getExerciseSets(day, workoutId, stepKeys.toArray(new String[0]));
                        }
                );
            }
            Log.i(TAG, "loadExercises: Room not yet initialized, returning null");
            return new MutableLiveData<>();
        });
        exercises.addSource(currentExerciseSource, exerciseSets -> {
            if (exerciseSets == null){
                Log.d(TAG, "loadExercises: null exerciseSets");
                return;
            }
            Log.i(TAG, "loadExercises: detected " + exerciseSets.size() + " new exercise sets, loading exercise descriptions");
            for (ExerciseSet set : exerciseSets) {
                set.setExercise(Transformations.switchMap(room, roomDb -> {
                    if (roomDb != null) {
                        return roomDb.getExerciseDao().getExercise(set.getName(), set.getWorkout());
                    }
                    Log.w(TAG, "loadExercises: somehow Room was null, returning null for exercise description");
                    return new MutableLiveData<>();
                }));
            }
            Log.i(TAG, "loadExercises: setting final value of exercise livedata");
            exercises.setValue(exerciseSets);
        });
    }

    public void storeSetRecord(SetRecord record) {
        Log.i(TAG, "storeSetRecord: requesting record creation");
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
