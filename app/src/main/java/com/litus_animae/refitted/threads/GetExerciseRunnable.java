package com.litus_animae.refitted.threads;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.litus_animae.refitted.data.DynamoDataService;
import com.litus_animae.refitted.data.ExerciseRoom;
import com.litus_animae.refitted.data.RoomDataService;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GetExerciseRunnable implements Runnable {

    private static final String TAG = "GetExerciseRunnable";
    private Context applicationContext;
    private String day;
    private String workoutId;
    private ExerciseRoom roomDb;
    private DynamoDataService dynamoDb;
    private MutableLiveData<List<ExerciseSet>> exerciseSetLiveData;
    private MutableLiveData<List<ExerciseRecord>> exerciseRecordLiveData;

    public GetExerciseRunnable(Context context, MutableLiveData<List<ExerciseSet>> sets,
                               MutableLiveData<List<ExerciseRecord>> records,
                               String day, String workoutId) {
        this.applicationContext = context.getApplicationContext();
        this.day = day;
        this.workoutId = workoutId;
        this.exerciseSetLiveData = sets;
        this.exerciseRecordLiveData = records;
    }

    @Override
    public void run() {
        try {
            roomDb = RoomDataService.getExerciseRoom(applicationContext);
            dynamoDb = new DynamoDataService(applicationContext);

            Set<String> keys = getExerciseKeys(day, workoutId);
            ArrayList<ExerciseSet> exerciseSets = getExerciseSets(day, keys, workoutId);
            getExercises(workoutId, exerciseSets);

            if (!exerciseSets.isEmpty()) {
                exerciseSets.sort((o1, o2) -> o1.getStep().compareTo(o2.getStep()));

                Log.d(TAG, "run: posting sets");
                exerciseSetLiveData.postValue(exerciseSets);
                Log.d(TAG, "run: sets posted");

                ArrayList<ExerciseRecord> records = getExerciseRecords(exerciseSets);
                Log.d(TAG, "run: posting records");
                exerciseRecordLiveData.postValue(records);
                Log.d(TAG, "run: records posted");

                Log.d(TAG, "run: retrieval success");
            } else {
                Log.d(TAG, "run: retrieval failure");
            }
        } catch (Exception ex) {
            Log.e(TAG, "run: exception during load", ex);
        }
    }

    private ArrayList<ExerciseRecord> getExerciseRecords(ArrayList<ExerciseSet> exerciseSets) {
        ArrayList<ExerciseRecord> records = new ArrayList<>(exerciseSets.size());
        Date tonightMidnight = Date.from(LocalDateTime.now().toLocalDate().atStartOfDay().toInstant(ZoneOffset.ofHours(0)));
        for (ExerciseSet e : exerciseSets) {
            Log.d(TAG, "getExerciseRecords: storing " + e.getId() + "-" +
                    e.getWorkout() + ": '" + e.getName() + "' in cache");
            try {
                roomDb.getExerciseDao().storeExerciseSet(e);
            } catch (Exception ex) {
                // TODO report to me
                Log.e(TAG, "getExerciseRecords: error storing exercise set cache", ex);
                Exercise exercise = new Exercise();
                exercise.setId(e.getName());
                exercise.setWorkout(e.getWorkout());
                roomDb.getExerciseDao().storeExercise(exercise);
                roomDb.getExerciseDao().storeExerciseSet(e);
            }
            ExerciseRecord record = new ExerciseRecord(e);
            try {
                record.setSets(roomDb.getExerciseDao()
                        .getSetRecords(tonightMidnight, e.getExerciseName()));
            } catch (Exception ex) {
                Log.e(TAG, "getExerciseRecords: failed retrieving records", ex);
            }
            records.add(record);
        }
        return records;
    }

    private void getExercises(String workoutId,
                              ArrayList<ExerciseSet> exerciseSets) {
        if (exerciseSets.isEmpty()) {
            Log.e(TAG, "getExercises: result set was empty");
            return;
        }
        Set<String> exercises = new HashSet<>();
        for (ExerciseSet e : exerciseSets) {
            exercises.add(e.getName());
        }
        Log.i(TAG, "getExercises: retrieving distinct exercises");
        Map<String, Exercise> exerciseMap = new HashMap<>();
        for (String e : exercises) {
            Exercise exercise = roomDb.getExerciseDao().getExercise(e, workoutId);
            if (exercise == null) {
                exercise = dynamoDb.getExercise(e, workoutId);
                if (exercise != null) {
                    roomDb.getExerciseDao().storeExercise(exercise);
                }
            }
            if (exercise != null) {
                exerciseMap.put(e, exercise);
            }
        }
        Exercise errExercise = new Exercise();
        errExercise.setDescription("Error loading");
        // TODO notify of errors??
        for (ExerciseSet e : exerciseSets) {
            e.setExercise(exerciseMap.getOrDefault(e.getName(), errExercise));
        }
        Log.i(TAG, "getExercises: retrieved distinct exercises");
    }

    private ArrayList<ExerciseSet> getExerciseSets(String day, Set<String> exercises,
                                                   String workoutId) {
        Log.i(TAG, "getExerciseSets: retrieving exercise sets for day: " + day +
                " from workout: " + workoutId);
        ArrayList<ExerciseSet> exerciseSets = new ArrayList<>();
        for (String e : exercises) {
            ExerciseSet exerciseSet = roomDb.getExerciseDao().getExerciseSet(day, workoutId, e);
            if (exerciseSet == null) {
                Log.i(TAG, "getExerciseSets: missed cache for " + day + "." + e +
                        " in workout " + workoutId);
                exerciseSet = dynamoDb.getExerciseSet(day, e, workoutId);
            }
            if (exerciseSet != null) {
                exerciseSets.add(exerciseSet);
            } else {
                Log.e(TAG, "getExerciseSets: exercise not found! " + day + "." + e +
                        " in workout " + workoutId);
            }
        }
        return exerciseSets;
    }

    private Set<String> getExerciseKeys(String day, String workoutId) {
        Log.i(TAG, "getExerciseKeys: retrieving exercise set ids for day: " + day +
                " from workout: " + workoutId);
        Set<String> exerciseKeys = new HashSet<>(roomDb.getExerciseDao().getSteps(day, workoutId));
        if (!exerciseKeys.isEmpty()) {
            Log.i(TAG, "getExerciseKeys: found keys in cache");
            return exerciseKeys;
        }
        Log.d(TAG, "getExerciseKeys: did not find keys in cache, checking dynamo");
        try {
            exerciseKeys = dynamoDb.getExerciseKeys(day, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "getExerciseKeys: error loading workout", ex);
        }
        return exerciseKeys;
    }
}
