package com.litus_animae.refitted.threads;

import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.litus_animae.refitted.Constants;
import com.litus_animae.refitted.data.DynamoDataService;
import com.litus_animae.refitted.data.ExerciseRoom;
import com.litus_animae.refitted.data.RoomDataService;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GetExerciseRunnable implements Runnable, Thread.UncaughtExceptionHandler {

    private static final String TAG = "GetExerciseRunnable";
    private Context applicationContext;
    private String day;
    private String workoutId;
    private ExerciseRoom roomDb;
    private DynamoDataService dynamoDb;
    private MutableLiveData<List<ExerciseSet>> exerciseSetLiveData;

    public GetExerciseRunnable(Context context, MutableLiveData<List<ExerciseSet>> sets,
                               String day, String workoutId) {
        this.applicationContext = context.getApplicationContext();
        this.day = day;
        this.workoutId = workoutId;
        this.exerciseSetLiveData = sets;
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void run() {
        try {
            roomDb = RoomDataService.GetExerciseRoom(applicationContext);
            dynamoDb = new DynamoDataService(applicationContext);

            Set<String> keys = GetExerciseKeys(day, workoutId);
            ArrayList<ExerciseSet> exerciseSets = GetExerciseSets(day, keys, workoutId);
            GetExercises(workoutId, exerciseSets);

            Message msg;
            if (!exerciseSets.isEmpty()) {
                exerciseSets.sort((o1, o2) -> o1.getStep().compareTo(o2.getStep()));
                Log.d(TAG, "run: posting data");
                exerciseSetLiveData.postValue(exerciseSets);
                Log.d(TAG, "run: data posted");

                ArrayList<ExerciseRecord> records = new ArrayList<>(exerciseSets.size());
                for (ExerciseSet e : exerciseSets) {
                    roomDb.getExerciseDao().storeExerciseSet(e);
                    records.add(new ExerciseRecord(e));
                }

                Log.d(TAG, "run: retrieval success");
                msg = Message.obtain(null, Constants.EXERCISE_LOAD_SUCCESS);
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList("exercise_load", exerciseSets);
                bundle.putParcelableArrayList("exercise_records", records);
                msg.setData(bundle);
            } else {
                Log.d(TAG, "run: retrieval failure");
                msg = Message.obtain(null, Constants.EXERCISE_LOAD_FAIL);
            }
        } catch (Exception ex) {
            Log.e(TAG, "run: ", ex);
        }
    }

    private void GetExercises(String workoutId,
                              ArrayList<ExerciseSet> exerciseSets) {
        if (exerciseSets.isEmpty()) {
            Log.e(TAG, "GetExercises: result set was empty");
            return;
        }
        Set<String> exercises = new HashSet<>();
        for (ExerciseSet e : exerciseSets) {
            exercises.add(e.getName());
        }
        Log.d(TAG, "GetExercises: retrieving distinct exercises");
        Map<String, Exercise> exerciseMap = new HashMap<>();
        for (String e : exercises) {
            Exercise exercise = roomDb.getExerciseDao().getExercise(e, workoutId);
            if (exercise == null) {
                exercise = dynamoDb.GetExercise(e, workoutId);
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
        for (ExerciseSet e : exerciseSets) {
            e.setExercise(exerciseMap.getOrDefault(e.getName(), errExercise));
        }
    }

    private ArrayList<ExerciseSet> GetExerciseSets(String day, Set<String> exercises,
                                                   String workoutId) {
        Log.d(TAG, "GetExerciseSets: retrieving exercise sets for day: " + day +
                " from workout: " + workoutId);
        ArrayList<ExerciseSet> exerciseSets = new ArrayList<>();
        for (String e : exercises) {
            ExerciseSet exerciseSet = roomDb.getExerciseDao().getExerciseSet(day, workoutId, e);
            if (exerciseSet == null) {
                Log.d(TAG, "GetExerciseSets: missed cache for " + day + "." + e +
                        " in workout " + workoutId);
                exerciseSet = dynamoDb.GetExerciseSet(day, e, workoutId);
            }
            if (exerciseSet != null) {
                exerciseSets.add(exerciseSet);
            } else {
                Log.e(TAG, "GetExerciseSets: exercise not found! " + day + "." + e +
                        " in workout " + workoutId);
            }
        }
        return exerciseSets;
    }

    private Set<String> GetExerciseKeys(String day, String workoutId) {
        Log.d(TAG, "GetExerciseKeys: retrieving exercise set ids for day: " + day +
                " from workout: " + workoutId);
        Set<String> exerciseKeys = new HashSet<>(roomDb.getExerciseDao().getSteps(day, workoutId));
        if (!exerciseKeys.isEmpty()) {
            Log.d(TAG, "GetExerciseKeys: found keys in cache");
            return exerciseKeys;
        }
        Log.d(TAG, "GetExerciseKeys: did not find keys in cache, checking dynamo");
        try {
            exerciseKeys = dynamoDb.GetExerciseKeys(day, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "GetExerciseKeys: error loading workout", ex);
        }
        return exerciseKeys;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.e(TAG, "uncaughtException: thread: " + t.getName(), e);
    }
}
