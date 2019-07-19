package com.litus_animae.refitted.data;

import android.content.Context;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.WorkoutDay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DynamoDataService {
    private static final String TAG = "DynamoDataService";
    private DynamoDBMapper db;

    public DynamoDataService(Context applicationContext) {
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
        db = DynamoDBMapper.builder()
                .dynamoDBClient(dbClient)
                .dynamoDBMapperConfig(new DynamoDBMapperConfig(
                        new DynamoDBMapperConfig.TableNameOverride(tableName)))
                .build();
    }

    public void GetExercises(String workoutId,
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
            exerciseMap.put(e, GetExercise(e, workoutId));
        }
        Exercise errExercise = new Exercise();
        errExercise.setDescription("Error loading");
        for (ExerciseSet e : exerciseSets) {
            e.setExercise(exerciseMap.getOrDefault(e.getName(), errExercise));
        }
    }

    private Exercise GetExercise(String exerciseId, String workoutId) {
        Log.d(TAG, "GetExercise: retrieving exercise: " + exerciseId +
                " from workout: " + workoutId);
        try {
            return db.load(Exercise.class, exerciseId, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "GetExercise: error loading Exercise", ex);
        }
        return null;
    }

    public ArrayList<ExerciseSet> GetExerciseSets(String day, String workoutId) {
        Log.d(TAG, "GetExerciseSets: retrieving exercise set ids for day: " + day +
                " from workout: " + workoutId);
        Set<String> exercises = GetExerciseKeys(day, workoutId);
        if (exercises.isEmpty()) {
            Log.e(TAG, "GetExerciseSets: result set was empty");
            return new ArrayList<>();
        }
        Log.d(TAG, "GetExerciseSets: retrieving exercise sets for day: " + day +
                " from workout: " + workoutId);
        ArrayList<ExerciseSet> exerciseSets = new ArrayList<>();
        for (String e : exercises) {
            ExerciseSet exerciseSet = GetExerciseSet(day, e, workoutId);
            if (exerciseSet != null) {
                exerciseSets.add(exerciseSet);
            }
        }
        return exerciseSets;
    }

    private ExerciseSet GetExerciseSet(String day, String exercise, String workoutId) {
        try {
            return db.load(ExerciseSet.class, day + "." + exercise, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "GetExerciseSet: error loading Exercise Set", ex);
            return null;
        }
    }

    private Set<String> GetExerciseKeys(String day, String workoutId) {
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
}
