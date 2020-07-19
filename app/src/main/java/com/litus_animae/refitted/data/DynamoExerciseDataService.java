package com.litus_animae.refitted.data;

import android.content.Context;
import android.util.Log;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;

public class DynamoExerciseDataService extends DynamoDataService {
    private static final String TAG = "DynamoExerciseDataService";
    private DynamoDBQueryExpression<ExerciseSet> queryExpression;
    private String day;
    private String workout;

    DynamoExerciseDataService(Context applicationContext, ExerciseRoom room) {
        super(applicationContext, room);
    }

    @Override
    protected Void doInBackground(String... dayAndWorkoutId) {
        // TODO check the array
        ExerciseSet keyValues = new ExerciseSet(dayAndWorkoutId[1]);

        Condition rangeCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
                .withAttributeValueList(new AttributeValue().withS(dayAndWorkoutId[0] + "."));

        queryExpression = new DynamoDBQueryExpression<ExerciseSet>()
                .withHashKeyValues(keyValues)
                .withIndexName("Reverse-index")
                .withRangeKeyCondition("Id", rangeCondition)
                .withConsistentRead(false);

        day = dayAndWorkoutId[0];
        workout = dayAndWorkoutId[1];

        Log.i(TAG, "doInBackground: Sending query request to load day " + dayAndWorkoutId[0] +
                " from workout " + dayAndWorkoutId[1]);

        connectAndRun();
        return null;
    }

    @Override
    protected void runAfterConnect() {
        try {
            PaginatedQueryList<ExerciseSet> result = dynamoDb.query(ExerciseSet.class, queryExpression);

            Log.i(TAG, "doInBackground: Query results received");
            Log.i(TAG, "doInBackground: storing " + result.size() + " values in cache");
            result.forEach(set -> room.runInTransaction(() -> {
                if (set == null){
                    Log.w(TAG, "doInBackground: loaded set was null");
                    return;
                }
                try {
                    Exercise e = dynamoDb.load(Exercise.class, set.getName(), set.getWorkout());
                    if (e == null){
                        Log.w(TAG, "doInBackground: loaded exercise was null, replacing with default");
                        e = new Exercise(set.getWorkout(), set.getName());
                    }
                    room.getExerciseDao().storeExercise(e);
                    room.getExerciseDao().storeExerciseSet(set);
                } catch (Exception ex) {
                    Log.e(TAG, "doInBackground: error loading Exercise", ex);
                    Exercise e = new Exercise(set.getWorkout(), set.getName());
                    room.getExerciseDao().storeExercise(e);
                    room.getExerciseDao().storeExerciseSet(set);
                }
            }));
        } catch (Exception ex) {
            Log.e(TAG, "doInBackground: error loading ExerciseSets", ex);
        }
    }
}

