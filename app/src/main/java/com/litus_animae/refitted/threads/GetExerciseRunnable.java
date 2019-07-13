package com.litus_animae.refitted.threads;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.litus_animae.refitted.Constants;
import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.WorkoutDay;

public class GetExerciseRunnable implements Runnable {

    private static final String TAG = "GetExerciseRunnable";
    private WeakReference<Handler> mainThreadHandler;
    private Context applicationContext;
    private String day;
    private String workoutId;

    public GetExerciseRunnable(Context context, Handler mainThreadHandler,
                               String day, String workoutId) {
        this.applicationContext = context.getApplicationContext();
        this.mainThreadHandler = new WeakReference<>(mainThreadHandler);
        this.day = day;
        this.workoutId = workoutId;
    }

    @Override
    public void run() {
        DynamoDBMapper db = GetDatabaseMapper();
        ArrayList<ExerciseSet> exerciseSets = GetExerciseSets(db, day, workoutId);
        Message msg;
        if (!exerciseSets.isEmpty()) {
            GetExercises(db, workoutId, exerciseSets);
            // TODO apply order

            ArrayList<ExerciseRecord> records = new ArrayList<>(exerciseSets.size());
            for (ExerciseSet e : exerciseSets){
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
        mainThreadHandler.get().sendMessage(msg);
    }

    private static void GetExercises(DynamoDBMapper db, String workoutId,
                                     ArrayList<ExerciseSet> exerciseSets){
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
        for (String e : exercises){
            exerciseMap.put(e, GetExercise(db, e, workoutId));
        }
        Exercise errExercise = new Exercise();
        errExercise.setDescription("Error loading");
        for (ExerciseSet e : exerciseSets) {
            e.setExercise(exerciseMap.getOrDefault(e.getName(), errExercise));
        }
    }

    private static Exercise GetExercise(DynamoDBMapper db, String exerciseId, String workoutId) {
        Log.d(TAG, "GetExercise: retrieving exercise: " + exerciseId +
                " from workout: " + workoutId);
        try {
            return db.load(Exercise.class, exerciseId, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "GetExercise: error loading Exercise", ex);
        }
        return null;
    }

    private static ArrayList<ExerciseSet> GetExerciseSets(DynamoDBMapper db, String day, String workoutId) {
        Log.d(TAG, "GetExerciseSets: retrieving exercise set ids for day: " + day +
                " from workout: " + workoutId);
        Set<String> exercises = GetExerciseKeys(db, day, workoutId);
        if (exercises.isEmpty()) {
            Log.e(TAG, "GetExerciseSets: result set was empty");
            return new ArrayList<>();
        }
        Log.d(TAG, "GetExerciseSets: retrieving exercise sets for day: " + day +
                " from workout: " + workoutId);
        ArrayList<ExerciseSet> exerciseSets = new ArrayList<>();
        for (String e : exercises) {
            ExerciseSet exerciseSet = GetExerciseSet(db, day, e, workoutId);
            if (exerciseSet != null) {
                exerciseSets.add(exerciseSet);
            }
        }
        return exerciseSets;
    }

    private static ExerciseSet GetExerciseSet(DynamoDBMapper db, String day, String exercise, String workoutId) {
        try {
            return db.load(ExerciseSet.class, day + "." + exercise, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "GetExerciseSet: error loading Exercise Set", ex);
            return null;
        }
    }

    private static Set<String> GetExerciseKeys(DynamoDBMapper db, String day, String workoutId) {
        try {
            WorkoutDay workout = db.load(WorkoutDay.class, day, workoutId);
            if (workout != null) {
                return workout.getExercises();
            }
        } catch (Exception ex) {
            Log.e(TAG, "GetExerciseKeys: error loading workout", ex);
        }
        return new HashSet<>();
    }

    private DynamoDBMapper GetDatabaseMapper() {
        CognitoCachingCredentialsProvider credentialsProvider =
                new CognitoCachingCredentialsProvider(
                        applicationContext,
                        "***REMOVED***",
                        Regions.US_EAST_2
                );
        AmazonDynamoDBClient dbClient = new AmazonDynamoDBClient(credentialsProvider);
        dbClient.setRegion(Region.getRegion(Regions.US_EAST_2));
        String tableName = applicationContext.getString(R.string.dynamo_table);
        Log.d(TAG, "GetDatabaseMapper: generating mapper to table: " + tableName);
        return DynamoDBMapper.builder()
                .dynamoDBClient(dbClient)
                .dynamoDBMapperConfig(new DynamoDBMapperConfig(
                        new DynamoDBMapperConfig.TableNameOverride(tableName)))
                .build();
    }
}
