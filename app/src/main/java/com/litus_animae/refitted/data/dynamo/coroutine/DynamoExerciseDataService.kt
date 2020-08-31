package com.litus_animae.refitted.data.dynamo.coroutine

import android.content.Context
import android.util.Log
import arrow.core.extensions.list.foldable.traverse_
import arrow.core.extensions.list.traverse.traverse
import arrow.fx.IO
import arrow.fx.extensions.fx
import arrow.fx.extensions.io.applicative.applicative
import arrow.fx.extensions.io.concurrent.parTraverse
import arrow.fx.fix
import arrow.fx.handleError
import arrow.fx.handleErrorWith
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.Condition
import com.litus_animae.refitted.data.room.ExerciseRoom
import com.litus_animae.refitted.models.DynamoExerciseSet
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.MutableExercise
import com.litus_animae.refitted.models.RoomExerciseSet
import kotlinx.coroutines.Dispatchers

class DynamoExerciseDataService(applicationContext: Context, room: ExerciseRoom) : DynamoDataService(applicationContext, room) {
    private var queryExpression: DynamoDBQueryExpression<DynamoExerciseSet>? = null
    fun execute(day: String, workoutId: String): IO<Unit> {
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

    override fun runAfterConnect(): IO<Unit> {
        return IO.fx {
            continueOn(Dispatchers.IO)
            val result = !IO { dynamoDb!!.query(DynamoExerciseSet::class.java, queryExpression!!) }
            Log.i(TAG, "doInBackground: Query results received")
            Log.i(TAG, "doInBackground: storing " + result.size + " values in cache")
            //TODO should this be parTraverse?
            !result.toList().traverse_(IO.applicative()) { set ->
                IO.fx {
                    continueOn(Dispatchers.IO)
                    val mutableExercise = !effect { dynamoDb!!.load(MutableExercise::class.java, set.name, set.workout) }
                    val exercise = Exercise(mutableExercise)
                    val exerciseSet = RoomExerciseSet(set)
                    !effect{ room.getExerciseDao().storeExerciseAndSet(exercise, exerciseSet) }
                }.handleErrorWith { ex ->
                    IO.fx {
                        Log.e(TAG, "doInBackground: error loading Exercise", ex)
                        val exercise = Exercise(set.workout, set.name)
                        val exerciseSet = RoomExerciseSet(set)
                        !effect { room.getExerciseDao().storeExerciseAndSet(exercise, exerciseSet)}
                    }.handleError { ex -> Log.wtf(TAG, "doInBackground: error loading Exercise", ex)}
                }
            }
        }.handleError { ex -> Log.e(TAG, "doInBackground: error loading ExerciseSets", ex) }
    }

    companion object {
        private const val TAG = "DynamoExerciseDataService"
    }
}