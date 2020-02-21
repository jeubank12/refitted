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
import com.litus_animae.refitted.threads.StoreRecordsRunnable;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseRepository {

    private static final String TAG = "ExerciseRepository";

    private WeakReference<Context> applicationContext;
    private ExecutorService threadPoolService;
    // TODO remove this and only access sets via records
    private MediatorLiveData<List<ExerciseSet>> exercises = new MediatorLiveData<>();
    private MediatorLiveData<HashSet<String>> changedStepsSource = new MediatorLiveData<>();
    private LiveData<HashSet<String>> currentStepsSource = null;
    private LiveData<List<ExerciseSet>> currentSetsSource = null;
    private LiveData<List<ExerciseRecord>> records;
    private Comparator<ExerciseSet> compareByStep = Comparator.comparing(ExerciseSet::getStep);

    public ExerciseRepository(Context context) {
        applicationContext = new WeakReference<>(context.getApplicationContext());
        threadPoolService = Executors.newCachedThreadPool();

        LiveData<ExerciseRoom> room = RoomDataService.getExerciseRoomAsync(applicationContext);
        records = Transformations.switchMap(room, this::getRecordsForLoadedExercises);
    }

    private LiveData<List<ExerciseRecord>> getRecordsForLoadedExercises(ExerciseRoom roomDb) {
        if (roomDb != null) {
            return Transformations.map(exercises, loadedExercises -> {
                if (loadedExercises == null) {
                    return new ArrayList<>();
                }
                Log.i(TAG, "getRecordsForLoadedExercises: detected " + loadedExercises.size() + " new exercises, loading records");
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
                        Log.e(TAG, "getRecordsForLoadedExercises: failed retrieving records", ex);
                    }
                    recordObjects.add(record);
                }
                Log.i(TAG, "getRecordsForLoadedExercises: records loaded");
                return recordObjects;
            });
        }
        Log.i(TAG, "getRecordsForLoadedExercises: Room not yet initialized, returning empty list");
        MutableLiveData<List<ExerciseRecord>> emptyResult = new MutableLiveData<>();
        emptyResult.setValue(new ArrayList<>());
        return emptyResult;
    }

    public void loadExercises(String day, String workoutId) {
        LiveData<ExerciseRoom> room = RoomDataService.getExerciseRoomAsync(applicationContext);
        if (currentSetsSource != null) {
            Log.d(TAG, "loadExercises: removing previously loaded exercises");
            exercises.removeSource(currentSetsSource);
        }
        if (currentStepsSource != null) {
            Log.d(TAG, "loadExercises: removing previously loaded steps");
            changedStepsSource.removeSource(currentStepsSource);
        }

        Log.i(TAG, "loadExercises: setting up stepsSource");
        currentStepsSource = Transformations.switchMap(room,
                roomDb -> getStepsForDayAndWorkout(day, workoutId, roomDb));

        Log.i(TAG, "loadExercises: setting up change detector for steps");
        changedStepsSource.addSource(currentStepsSource, this::updateSetsIfChanged);

        Log.i(TAG, "loadExercises: setting up sets loader based on steps changes");
        currentSetsSource = Transformations.switchMap(room,
                roomDb -> getExercisesFromSteps(day, workoutId, roomDb));

        Log.i(TAG, "loadExercises: setting up transformation to load exercise descriptions");
        exercises.addSource(currentSetsSource,
                exerciseSets -> updateExercisesWithMinimumChange(room, exerciseSets));
    }

    private void updateExercisesWithMinimumChange(LiveData<ExerciseRoom> room, List<ExerciseSet> exerciseSets) {
        if (exerciseSets == null) {
            Log.d(TAG, "updateExercisesWithMinimumChange: null exerciseSets");
            return;
        }
        Log.i(TAG, "updateExercisesWithMinimumChange: detected " + exerciseSets.size() + " new exercise sets, loading exercise descriptions");
        exerciseSets.sort(compareByStep);
        List<ExerciseSet> oldVals = exercises.getValue();
        if (oldVals == null) {
            oldVals = new ArrayList<>();
        }

        for (ExerciseSet set : exerciseSets) {
            ExerciseSet existing = getSetWithMatchingExercise(oldVals, set);
            if (existing != null) {
                Log.d(TAG, "updateExercisesWithMinimumChange: reusing existing query for " + existing.getName());
                set.setExercise(existing.getExercise());
            } else {
                set.setExercise(Transformations.switchMap(room, roomDb -> {
                    if (roomDb != null) {
                        Log.d(TAG, "updateExercisesWithMinimumChange: getting new query for " + set.getName());
                        return roomDb.getExerciseDao().getExercise(set.getName(), set.getWorkout());
                    }
                    Log.w(TAG, "updateExercisesWithMinimumChange: somehow Room was null, returning null for exercise description");
                    return new MutableLiveData<>();
                }));
            }
        }
        Log.i(TAG, "updateExercisesWithMinimumChange: setting final value of exercise livedata");
        exercises.setValue(exerciseSets);
    }

    private LiveData<List<ExerciseSet>> getExercisesFromSteps(String day, String workoutId, ExerciseRoom roomDb) {
        if (roomDb != null) {
            return Transformations.switchMap(changedStepsSource,
                    steps -> {
                        Log.i(TAG, "getExercisesFromSteps: steps updated, reloading sets");
                        return roomDb.getExerciseDao().getExerciseSets(day, workoutId, steps.toArray(new String[0]));
                    });
        }
        Log.i(TAG, "getExercisesFromSteps: Room not yet initialized, returning null");
        return new MutableLiveData<>();
    }

    private void updateSetsIfChanged(HashSet<String> stepKeys) {
        Log.i(TAG, "updateSetsIfChanged: received new values for steps");
        if (stepKeys.size() < 1){
            Log.i(TAG, "updateSetsIfChanged: no keys loaded yet, waiting...");
            return;
        }
        List<ExerciseSet> lastExercises = currentSetsSource.getValue();
        if (lastExercises != null && stepKeys.size() == lastExercises.size()) {
            if (doListsFullyIntersect(new ArrayList<>(stepKeys), lastExercises)) {
                Log.i(TAG, "updateSetsIfChanged: exercise set numbers were updated, but there are no changes");
                return;
            }
        }
        if (lastExercises == null) {
            Log.i(TAG, "updateSetsIfChanged: found exercise set numbers, updating the livedata");
        } else {
            Log.i(TAG, "updateSetsIfChanged: found new exercise set numbers, updating the livedata");
        }
        changedStepsSource.setValue(stepKeys);
    }

    private LiveData<HashSet<String>> getStepsForDayAndWorkout(String day, String workoutId, ExerciseRoom roomDb) {
        if (roomDb != null) {
            Log.i(TAG, "getStepsForDayAndWorkout: submitting dynamo query for workout " + workoutId + ", day " + day);
            DynamoExerciseDataService dynamoService = new DynamoExerciseDataService(applicationContext.get(), roomDb);
            dynamoService.execute(day, workoutId);
            Log.i(TAG, "getStepsForDayAndWorkout: returning query for workout steps");
            return Transformations.map(
                    roomDb.getExerciseDao().getSteps(day, workoutId),
                    HashSet::new);
        }
        Log.i(TAG, "getStepsForDayAndWorkout: Room not yet initialized, returning null");
        return new MutableLiveData<>();
    }

    private ExerciseSet getSetWithMatchingExercise(List<ExerciseSet> oldVals, ExerciseSet set) {
        for (ExerciseSet oldVal : oldVals) {
            if (oldVal.getName().equals(set.getName())) {
                return oldVal;
            }
        }
        return null;
    }

    private boolean doListsFullyIntersect(List<String> stepKeys, List<ExerciseSet> lastExercises) {
        stepKeys.sort(String::compareTo);
        lastExercises.sort(compareByStep);
        Iterator<String> newVals = stepKeys.iterator();
        Iterator<ExerciseSet> oldVals = lastExercises.iterator();
        while (newVals.hasNext()) {
            String newVal = newVals.next();
            ExerciseSet oldVal = oldVals.next();
            if (!newVal.equals(oldVal.getStep())) {
                return false;
            }
        }
        return true;
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
        RoomDataService.closeExerciseRoomAsync(applicationContext.get());
    }
}
