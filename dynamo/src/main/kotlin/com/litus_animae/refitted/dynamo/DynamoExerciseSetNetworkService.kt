package com.litus_animae.refitted.dynamo

import android.content.Context
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.litus_animae.refitted.data.models.DayAndWorkout
import com.litus_animae.refitted.data.models.Exercise
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.data.network.NetworkExerciseSet
import com.litus_animae.refitted.dynamo.DynamoUtil.queryReverseIndex
import com.litus_animae.refitted.dynamo.entities.DynamoExercise
import com.litus_animae.refitted.dynamo.entities.DynamoExerciseSet
import com.litus_animae.refitted.identity.AuthProvider
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DynamoExerciseSetNetworkService @Inject constructor(
  @ApplicationContext context: Context,
  private val log: LogUtil,
  authProvider: AuthProvider,
) :
  ExerciseSetNetworkService {
  private val dynamo = DynamoNetworkService(context, log, authProvider)

  override suspend fun getExerciseSets(dayAndWorkout: DayAndWorkout): List<NetworkExerciseSet> {
    val (workoutDay, workoutId) = dayAndWorkout
    return withContext(Dispatchers.IO) {
      val db = dynamo.getDb()

      val keyValues = DynamoExerciseSet(workoutId)
      log.i(
        TAG,
        "Sending query request to load day $workoutDay from workout $workoutId"
      )

      val dynamoSets =
        db.queryReverseIndex(
          DynamoExerciseSet::class.java,
          keyValues,
          "$workoutDay.",
          ComparisonOperator.BEGINS_WITH
        )
      log.i(TAG, "Query results received for day $workoutDay from workout $workoutId")
      val exerciseSets = dynamoSets.map { set ->
        try {
          log.i(TAG, "loading exercise ${set.workout}: ${set.name} from dynamo")
          val dynamoExercise =
            db.queryReverseIndex(
              DynamoExercise::class.java,
              DynamoExercise(workout = set.workout),
              set.name
            ).firstOrNull()
          val exercise = dynamoExercise?.toDomain() ?: Exercise(set.workout, set.name, null)
          dynamoExercise ?: log.w(TAG, "Exercise not found")
          NetworkExerciseSet(set.toDomain(), exercise)
        } catch (ex: Exception) {
          log.e(TAG, "error loading Exercise", ex)
          // create an empty exercise
          val exercise = Exercise(set.workout, set.name, null)
          NetworkExerciseSet(set.toDomain(), exercise)
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