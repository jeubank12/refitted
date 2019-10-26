package com.litus_animae.refitted.data;

import android.content.Context;
import android.util.Log;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.bugsee.library.Bugsee;
import com.bugsee.library.events.BugseeLogLevel;
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
        ExerciseSet keyValues = new ExerciseSet();
        // TODO check the array
        keyValues.setWorkout(dayAndWorkoutId[1]);

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
                    Bugsee.log(workout + "-" + day + ": a set had error loading from dynamo",
                            BugseeLogLevel.Warning);
                    Log.w(TAG, "doInBackground: loaded set was null");
                    return;
                }
                try {
                    Exercise e = dynamoDb.load(Exercise.class, set.getName(), set.getWorkout());
                    if (e == null){
                        Bugsee.log(set.getWorkout() + "-" + set.getName() + ": exercise had error loading from dynamo",
                                BugseeLogLevel.Warning);
                        Log.w(TAG, "doInBackground: loaded exercise was null, replacing with default");
                        e = new Exercise();
                        e.setId(set.getName());
                        e.setWorkout(set.getWorkout());
                    }
                    room.getExerciseDao().storeExercise(e);
                    room.getExerciseDao().storeExerciseSet(set);
                } catch (Exception ex) {
                    Bugsee.logException(ex);
                    Log.e(TAG, "doInBackground: error loading Exercise", ex);
                    Exercise e = new Exercise();
                    e.setId(set.getName());
                    e.setWorkout(set.getWorkout());
                    room.getExerciseDao().storeExercise(e);
                    room.getExerciseDao().storeExerciseSet(set);
                }
            }));
        } catch (Exception ex) {
            Bugsee.logException(ex);
            Log.e(TAG, "doInBackground: error loading ExerciseSets", ex);
        }
    }
}

