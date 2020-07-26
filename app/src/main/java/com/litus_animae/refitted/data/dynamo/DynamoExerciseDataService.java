package com.litus_animae.refitted.data.dynamo;

import android.content.Context;
import android.util.Log;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.litus_animae.refitted.data.room.ExerciseRoom;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.MutableExercise;
import com.litus_animae.refitted.models.MutableExerciseSet;

public class DynamoExerciseDataService extends DynamoDataService {
    private static final String TAG = "DynamoExerciseDataService";
    private DynamoDBQueryExpression<MutableExerciseSet> queryExpression;
    private String day;
    private String workout;

    public DynamoExerciseDataService(Context applicationContext, ExerciseRoom room) {
        super(applicationContext, room);
    }

    @Override
    protected Void doInBackground(String... dayAndWorkoutId) {
        // TODO check the array
        MutableExerciseSet keyValues = new MutableExerciseSet(dayAndWorkoutId[1]);

        Condition rangeCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
                .withAttributeValueList(new AttributeValue().withS(dayAndWorkoutId[0] + "."));

        queryExpression = new DynamoDBQueryExpression<MutableExerciseSet>()
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
            PaginatedQueryList<MutableExerciseSet> result = dynamoDb.query(MutableExerciseSet.class, queryExpression);

            Log.i(TAG, "doInBackground: Query results received");
            Log.i(TAG, "doInBackground: storing " + result.size() + " values in cache");
            result.forEach(set -> room.runInTransaction(() -> {
                if (set == null){
                    Log.w(TAG, "doInBackground: loaded set was null");
                    return;
                }
                try {
                    MutableExercise e = dynamoDb.load(MutableExercise.class, set.getName(), set.getWorkout());
                    if (e == null){
                        Log.w(TAG, "doInBackground: loaded exercise was null, replacing with default");
                        e = new MutableExercise(set.getWorkout(), set.getName());
                    }
                    Exercise modelExercise = new Exercise(e);
                    room.getExerciseDao().storeExercise(modelExercise);
                    ExerciseSet modelSet = new ExerciseSet(set);
                    room.getExerciseDao().storeExerciseSet(modelSet);
                } catch (Exception ex) {
                    Log.e(TAG, "doInBackground: error loading Exercise", ex);
                    Exercise e = new Exercise(set.getWorkout(), set.getName());
                    room.getExerciseDao().storeExercise(e);
                    ExerciseSet modelSet = new ExerciseSet(set);
                    room.getExerciseDao().storeExerciseSet(modelSet);
                }
            }));
        } catch (Exception ex) {
            Log.e(TAG, "doInBackground: error loading ExerciseSets", ex);
        }
    }
}

