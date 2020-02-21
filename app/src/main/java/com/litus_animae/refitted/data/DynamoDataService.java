package com.litus_animae.refitted.data;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.litus_animae.refitted.R;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public abstract class DynamoDataService extends AsyncTask<String, Void, Void> {
    private static final String TAG = "DynamoExerciseDataService";
    protected final ExerciseRoom room;
    private final CognitoCachingCredentialsProvider credentialsProvider;
    private final String tableName;
    DynamoDBMapper dynamoDb;
    private final Executor executor = Executors.newCachedThreadPool();
    private final String openIdSource;

    DynamoDataService(Context applicationContext, ExerciseRoom room) {
        this.room = room;
        credentialsProvider =
                new CognitoCachingCredentialsProvider(
                        applicationContext,
                        applicationContext.getString(R.string.cognito_identity_pool_id),
                        Regions.US_EAST_2
                );
        tableName = applicationContext.getString(R.string.dynamo_table);
        openIdSource = applicationContext.getString(R.string.firebase_id_source);

    }

    void connectAndRun() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null){
            return;
        }

        Map<String, String> logins = new HashMap<>();
        Task<GetTokenResult> idToken = currentUser.getIdToken(false);
        if (idToken.isSuccessful()) {
            GetTokenResult result = idToken.getResult();
            if (result != null){
                logins.put(openIdSource, result.getToken());
                connectToDynamo(logins);
            }
        } else {
            idToken.addOnCompleteListener(executor, task -> {
                logins.put(openIdSource, task.getResult().getToken());
                connectToDynamo(logins);
            });
        }
    }

    private void connectToDynamo(Map<String, String> logins) {
        credentialsProvider.setLogins(logins);

        Instant start = java.time.Instant.now();
        AmazonDynamoDBClient dbClient = new AmazonDynamoDBClient(credentialsProvider);
        dbClient.setRegion(Region.getRegion(Regions.US_EAST_2));
        Log.d(TAG, "GetDatabaseMapper: generating mapper to table: " + tableName);
        dynamoDb = DynamoDBMapper.builder()
                .dynamoDBClient(dbClient)
                .dynamoDBMapperConfig(new DynamoDBMapperConfig(
                        new DynamoDBMapperConfig.TableNameOverride(tableName)))
                .build();
        Instant endOpen = java.time.Instant.now();
        Log.d(TAG, "connectToDynamo: Dynamo took " + Duration.between(start, endOpen) + " to open. Started at: " + start.toString());
        runAfterConnect();
        Instant endQuery = java.time.Instant.now();
        Log.d(TAG, "connectToDynamo: Dynamo query took " + Duration.between(start, endQuery) + ". Started at: " + endOpen.toString());
    }

    protected abstract void runAfterConnect();
}

