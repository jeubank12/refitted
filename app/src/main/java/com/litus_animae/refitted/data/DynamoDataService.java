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

    public Exercise getExercise(String exerciseId, String workoutId) {
        Log.d(TAG, "getExercise: retrieving exercise: " + exerciseId +
                " from workout: " + workoutId);
        try {
            return db.load(Exercise.class, exerciseId, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "getExercise: error loading Exercise", ex);
        }
        return null;
    }

    public ExerciseSet getExerciseSet(String day, String exercise, String workoutId) {
        try {
            return db.load(ExerciseSet.class, day + "." + exercise, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "getExerciseSet: error loading Exercise Set", ex);
            return null;
        }
    }

    public Set<String> getExerciseKeys(String day, String workoutId) {
        Log.d(TAG, "getExerciseKeys: retrieving exercise set ids for day: " + day +
                " from workout: " + workoutId);
        try {
            WorkoutDay workout = db.load(WorkoutDay.class, day, workoutId);
            if (workout != null) {
                return workout.getExercises();
            }
        } catch (Exception ex) {
            Log.e(TAG, "getExerciseKeys: error loading workout", ex);
        }
        return new HashSet<>();
    }
}
