package com.litus_animae.refitted.threads;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.litus_animae.refitted.Constants;
import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;

public class GetExerciseRunnable implements Runnable {

    private static final String TAG = "GetExerciseRunnable";
    private WeakReference<Handler> mainThreadHandler;
    private Context applicationContext;
    private String dayAndSetId;
    private String workoutId;

    public GetExerciseRunnable(Context context, Handler mainThreadHandler,
                               String dayAndSetId, String workoutId) {
        this.applicationContext = context.getApplicationContext();
        this.mainThreadHandler = new WeakReference<>(mainThreadHandler);
        this.dayAndSetId = dayAndSetId;
        this.workoutId = workoutId;
    }

    @Override
    public void run() {
        DynamoDBMapper db = GetDatabaseMapper();
        ExerciseSet exerciseSet = GetSet(db, dayAndSetId, workoutId);
        if (exerciseSet != null) {
            exerciseSet.setExercise(GetExercise(db, exerciseSet.getName(), workoutId));
        }
        Message msg;
        if (exerciseSet != null && exerciseSet.getExercise() != null) {
            Log.d(TAG, "run: retrieval success");
            msg = Message.obtain(null, Constants.EXERCISE_LOAD_SUCCESS);
            Bundle bundle = new Bundle();
            bundle.putSerializable("exercise_load", exerciseSet);
            msg.setData(bundle);
        } else {
            Log.d(TAG, "run: retrieval failure");
            msg = Message.obtain(null, Constants.EXERCISE_LOAD_FAIL);
        }
        mainThreadHandler.get().sendMessage(msg);
    }

    private static Exercise GetExercise(DynamoDBMapper db, String exerciseId, String workoutId) {
        Log.d(TAG, "GetExercise: retriving exercise: " + exerciseId +
                " from workout: " + workoutId);
        try {
            return db.load(Exercise.class, exerciseId, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "GetExercise: error loading Exercise", ex);
            return null;
        }
    }

    private static ExerciseSet GetSet(DynamoDBMapper db, String dayAndSetId, String workoutId) {
        Log.d(TAG, "GetSet: retriving exercise set: " + dayAndSetId +
                " from workout: " + workoutId);
        try {
            return db.load(ExerciseSet.class, dayAndSetId, workoutId);
        } catch (Exception ex) {
            Log.e(TAG, "GetSet: error loading Exercise", ex);
            return null;
        }
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
