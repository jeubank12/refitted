package com.litus_animae.refitted.data;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedQueryList;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;

public class DynamoDataService extends AsyncTask<String, Void, Void> {
    private static final String TAG = "DynamoDataService";
    protected final DynamoDBMapper dynamoDb;
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
        dynamoDb = DynamoDBMapper.builder()
                .dynamoDBClient(dbClient)
                .dynamoDBMapperConfig(new DynamoDBMapperConfig(
                        new DynamoDBMapperConfig.TableNameOverride(tableName)))
                .build();
    }

    @Override
    protected Void doInBackground(String... dayAndWorkoutId) {
        ExerciseSet keyValues = new ExerciseSet();
        // TODO check the array
        keyValues.setWorkout(dayAndWorkoutId[1]);

        Condition rangeCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
                .withAttributeValueList(new AttributeValue().withS(dayAndWorkoutId[0] + "."));

        DynamoDBQueryExpression<ExerciseSet> queryExpression = new DynamoDBQueryExpression<ExerciseSet>()
                .withHashKeyValues(keyValues)
                .withIndexName("Reverse-index")
                .withRangeKeyCondition("Id", rangeCondition)
                .withConsistentRead(false);

        Log.i(TAG, "doInBackground: Sending query request to load day " + dayAndWorkoutId[0] +
                " from workout " + dayAndWorkoutId[1]);

        try {
            PaginatedQueryList<ExerciseSet> result = dynamoDb.query(ExerciseSet.class, queryExpression);

            Log.i(TAG, "doInBackground: Query results received");
            Log.i(TAG, "doInBackground: storing " + result.size() + " values in cache");
            room.runInTransaction(() -> result.forEach(set -> {
                if (set == null){
                    Log.w(TAG, "doInBackground: loaded set was null");
                    return;
                }
                try {
                    Exercise e = dynamoDb.load(Exercise.class, set.getName(), dayAndWorkoutId[1]);
                    if (e == null){
                        Log.w(TAG, "doInBackground: loaded exercise was null");
                        return;
                    }
                    room.getExerciseDao().storeExercise(e);
                    room.getExerciseDao().storeExerciseSet(set);
                } catch (Exception ex) {
                    Log.e(TAG, "doInBackground: error loading Exercise", ex);
                }
            }));
        } catch (Exception ex) {
            Log.e(TAG, "doInBackground: error loading ExerciseSets", ex);
        }
        return null;
    }
}

