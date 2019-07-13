package com.litus_animae.refitted.threads;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.logging.Level;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.litus_animae.refitted.Constants;
import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.Exercise;

public class GetExerciseRunnable implements Runnable {

    private static final String TAG = "GetExerciseRunnable";
    private WeakReference<Handler> mainThreadHandler;
    private Context applicationContext;
    private String exerciseId;
    private String workoutId;

    public GetExerciseRunnable(Context context, Handler mainThreadHandler,
                               String exerciseId, String workoutId) {
        this.applicationContext = context.getApplicationContext();
        this.mainThreadHandler = new WeakReference<>(mainThreadHandler);
        this.exerciseId = exerciseId;
        this.workoutId = workoutId;
    }

    @Override
    public void run() {
        DynamoDBMapper db = GetDatabaseMapper();
        Log.d(TAG, "run: retriving exercise: " + exerciseId + " from workout: " + workoutId);
        try {
            Exercise ex = db.load(Exercise.class, exerciseId, workoutId);
            Message msg;
            if (ex != null) {
                Log.d(TAG, "run: retrieval success");
                msg = Message.obtain(null, Constants.EXERCISE_LOAD_SUCCESS);
                Bundle bundle = new Bundle();
                bundle.putSerializable("exercise_load", ex);
                msg.setData(bundle);
            } else {
                Log.d(TAG, "run: retrieval failure");
                msg = Message.obtain(null, Constants.EXERCISE_LOAD_FAIL);
            }

            mainThreadHandler.get().sendMessage(msg);
        } catch (Exception ex){
            ex.printStackTrace();
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
