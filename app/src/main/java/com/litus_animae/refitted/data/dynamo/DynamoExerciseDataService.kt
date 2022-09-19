package com.litus_animae.refitted.data.dynamo

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.Condition
import com.litus_animae.refitted.data.room.RefittedRoom
import com.litus_animae.refitted.models.dynamo.MutableExerciseSet
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.dynamo.MutableExercise
import com.litus_animae.refitted.models.RoomExerciseSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

@WorkerThread
class DynamoExerciseDataService(applicationContext: Context, room: RefittedRoom) :
    DynamoDataService(applicationContext, room) {
    private var queryExpression: DynamoDBQueryExpression<MutableExerciseSet>? = null
    suspend fun execute(day: String, workoutId: String) {
        return withContext(Dispatchers.IO) {
            val keyValues = MutableExerciseSet(workoutId)
            val rangeCondition = Condition()
                .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
                .withAttributeValueList(AttributeValue().withS("$day."))
            queryExpression = DynamoDBQueryExpression<MutableExerciseSet>()
                .withHashKeyValues(keyValues)
                .withIndexName("Reverse-index")
                .withRangeKeyCondition("Id", rangeCondition)
                .withConsistentRead(false)
            Log.i(
                TAG,
                "doInBackground: Sending query request to load day $day from workout $workoutId"
            )
            connectAndRun()
        }
    }

    override suspend fun runAfterConnect() {
        try {
            val result = dynamoDb!!.query(MutableExerciseSet::class.java, queryExpression!!)
            Log.i(TAG, "doInBackground: Query results received")
            Log.i(TAG, "doInBackground: storing " + result.size + " values in cache")
            result.toList().map { set ->
                try {
                    Log.i(TAG, "doInBackground: loading ${set.workout}: ${set.name} from dynamo")
                    val mutableExercise =
                        dynamoDb!!.load(MutableExercise::class.java, set.name, set.workout)
                    val exercise = Exercise(mutableExercise)
                    val exerciseSet = RoomExerciseSet(set)
                    room.getExerciseDao().storeExerciseAndSet(exercise, exerciseSet)
                    Log.i(TAG, "doInBackground: stored ${set.workout}: ${set.name} in cache")
                } catch (ex: Exception) {
                    Log.e(TAG, "doInBackground: error loading Exercise", ex)
                    val exercise = Exercise(set.workout, set.name)
                    val exerciseSet = RoomExerciseSet(set)
                    room.getExerciseDao().storeExerciseAndSet(exercise, exerciseSet)
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "doInBackground: error loading ExerciseSets", ex)
        }
    }

    companion object {
        private const val TAG = "DynamoExerciseDataService"
    }
}