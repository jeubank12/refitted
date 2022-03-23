package com.litus_animae.refitted.data.dynamo

import android.content.Context
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.Condition
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.data.network.NetworkExerciseSet
import com.litus_animae.refitted.models.*
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DynamoExerciseSetNetworkService @Inject constructor(
    @ApplicationContext context: Context,
    log: LogUtil
) :
    DynamoNetworkService(context, log), ExerciseSetNetworkService {
    override suspend fun getExerciseSets(dayAndWorkout: DayAndWorkout): List<NetworkExerciseSet> {
        val (workoutDay, workoutId) = dayAndWorkout
        return withContext(Dispatchers.IO) {
            val db = getDb()

            val keyValues = MutableExerciseSet(workoutId)
            val rangeCondition = Condition()
                .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
                .withAttributeValueList(AttributeValue().withS("$workoutDay."))
            val queryExpression = DynamoDBQueryExpression<MutableExerciseSet>()
                .withHashKeyValues(keyValues)
                .withIndexName("Reverse-index")
                .withRangeKeyCondition("Id", rangeCondition)
                .withConsistentRead(false)
            log.i(
                TAG,
                "Sending query request to load day $workoutDay from workout $workoutId"
            )

            val dynamoSets = db.query(MutableExerciseSet::class.java, queryExpression)
            log.i(TAG, "Query results received for day $workoutDay from workout $workoutId")
            val exerciseSets = dynamoSets.map { set ->
                try {
                    log.i(TAG, "loading exercise ${set.workout}: ${set.name} from dynamo")
                    val mutableExercise =
                        db.load(MutableExercise::class.java, set.name, set.workout)
                    val exercise = Exercise(mutableExercise)
                    // TODO this is bad form, should move the constructors over to converters on the Room/Dynamo classes
                    NetworkExerciseSet(set, exercise)
                } catch (ex: Exception) {
                    log.e(TAG, "error loading Exercise", ex)
                    // create an empty exercise
                    val exercise = Exercise(set.workout, set.name)
                    NetworkExerciseSet(set, exercise)
                }
            }
            log.d(TAG, "Finished dynamo load $dayAndWorkout: $exerciseSets")
            exerciseSets
        }
    }

    companion object {
        private const val TAG = "DynamoExerciseSetNetworkService"
    }
}