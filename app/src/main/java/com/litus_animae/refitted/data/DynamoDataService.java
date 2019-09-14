package com.litus_animae.refitted.data;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.RoomDatabase;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedList;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.WorkoutDay;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DynamoDataService extends AsyncTask<String, Void, Void> {
    private static final String TAG = "DynamoDataService";
    protected final DynamoDBMapper db;
    private final ExerciseRoom room;

    public DynamoDataService(Context applicationContext, ExerciseRoom room) {
        this.room = room;
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

    @Override
    protected Void doInBackground(String... dayAndWorkoutId) {
        ExerciseSet keyValues = new ExerciseSet();
        keyValues.setWorkout(dayAndWorkoutId[1]);

        Condition rangeCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
                .withAttributeValueList(new AttributeValue().withS(dayAndWorkoutId[0] + "."));

        DynamoDBQueryExpression queryExpression = new DynamoDBQueryExpression<ExerciseSet>()
                .withHashKeyValues(keyValues)
                .withIndexName("Reverse-index")
                .withRangeKeyCondition("Id", rangeCondition)
                .withConsistentRead(false);

        Log.i(TAG, "doInBackground: Sending query request to load day " + dayAndWorkoutId[0] +
                " from workout " + dayAndWorkoutId[1]);

        PaginatedList<ExerciseSet> result = db.query(ExerciseSet.class, queryExpression);

        Log.i(TAG, "doInBackground: Query results received");
        Log.i(TAG, "doInBackground: storing " + result.size() + " values in cache");
        // TODO must fetch exercises and store first
        for (ExerciseSet set : result){
            try {
                Exercise e = db.load(Exercise.class, set.getName(), dayAndWorkoutId[1]);
                room.getExerciseDao().storeExercise(e);
                room.getExerciseDao().storeExerciseSet(set);
            } catch (Exception ex) {
                Log.e(TAG, "getExercise: error loading Exercise", ex);
            }
        }
        return null;
    }
}

