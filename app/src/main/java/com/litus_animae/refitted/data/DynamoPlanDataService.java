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
import com.litus_animae.refitted.models.WorkoutPlan;

public class DynamoPlanDataService extends DynamoDataService {
    private static final String TAG = "DynamoExerciseDataService";

    DynamoPlanDataService(Context applicationContext, ExerciseRoom room) {
        super(applicationContext, room);
    }

    @Override
    protected Void doInBackground(String... noInputRequired) {
        WorkoutPlan keyValues = new WorkoutPlan();

        DynamoDBQueryExpression<WorkoutPlan> queryExpression = new DynamoDBQueryExpression<WorkoutPlan>()
                .withHashKeyValues(keyValues)
                .withConsistentRead(false);

        Log.i(TAG, "doInBackground: Sending query request to load available workout plans");

        try {
            PaginatedQueryList<WorkoutPlan> result = dynamoDb.query(WorkoutPlan.class, queryExpression);

            Log.i(TAG, "doInBackground: Query results received");

        } catch (Exception ex) {
            Log.e(TAG, "doInBackground: error loading WorkoutPlans", ex);
        }
        return null;
    }
}

