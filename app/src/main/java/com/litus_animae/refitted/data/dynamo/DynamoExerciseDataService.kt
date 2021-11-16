package com.litus_animae.refitted.data.dynamo

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.Condition
import com.litus_animae.refitted.data.room.ExerciseRoom
import com.litus_animae.refitted.models.DynamoExerciseSet
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.MutableExercise
import com.litus_animae.refitted.models.RoomExerciseSet
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map

@WorkerThread
class DynamoExerciseDataService(applicationContext: Context, room: ExerciseRoom) :
    DynamoDataService(applicationContext, room) {
    private var queryExpression: DynamoDBQueryExpression<DynamoExerciseSet>? = null
    suspend fun execute(day: String, workoutId: String): Unit {
        val keyValues = DynamoExerciseSet(workoutId)
        val rangeCondition = Condition()
            .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
            .withAttributeValueList(AttributeValue().withS("$day."))
        queryExpression = DynamoDBQueryExpression<DynamoExerciseSet>()
            .withHashKeyValues(keyValues)
            .withIndexName("Reverse-index")
            .withRangeKeyCondition("Id", rangeCondition)
            .withConsistentRead(false)
        Log.i(TAG, "doInBackground: Sending query request to load day $day from workout $workoutId")
        return connectAndRun()
    }

    override suspend fun runAfterConnect() {
        try {
            val result = dynamoDb!!.query(DynamoExerciseSet::class.java, queryExpression!!)
            Log.i(TAG, "doInBackground: Query results received")
            Log.i(TAG, "doInBackground: storing " + result.size + " values in cache")
            result.toList().asFlow().map { set ->
                try {
                    val mutableExercise =
                        dynamoDb!!.load(MutableExercise::class.java, set.name, set.workout)
                    val exercise = Exercise(mutableExercise)
                    val exerciseSet = RoomExerciseSet(set)
                    room.getExerciseDao().storeExerciseAndSet(exercise, exerciseSet)
                } catch (ex: Throwable) {
                    Log.e(TAG, "doInBackground: error loading Exercise", ex)
                    val exercise = Exercise(set.workout, set.name)
                    val exerciseSet = RoomExerciseSet(set)
                    room.getExerciseDao().storeExerciseAndSet(exercise, exerciseSet)
                }
            }
        } catch (ex: Throwable) {
            Log.e(TAG, "doInBackground: error loading ExerciseSets", ex)
        }
    }

    companion object {
        private const val TAG = "DynamoExerciseDataService"
    }
}