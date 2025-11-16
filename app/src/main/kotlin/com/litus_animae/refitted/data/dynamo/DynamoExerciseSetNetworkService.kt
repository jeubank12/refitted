package com.litus_animae.refitted.data.dynamo

import android.content.Context
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.litus_animae.refitted.data.dynamo.DynamoUtil.queryReverseIndex
import com.litus_animae.refitted.data.firebase.AuthProvider
import com.litus_animae.refitted.data.network.ExerciseSetNetworkService
import com.litus_animae.refitted.data.network.NetworkExerciseSet
import com.litus_animae.refitted.models.DayAndWorkout
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.dynamo.MutableExercise
import com.litus_animae.refitted.models.dynamo.MutableExerciseSet
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
  log: LogUtil,
  authProvider: AuthProvider
) :
  DynamoNetworkService(context, log, authProvider), ExerciseSetNetworkService {
  override suspend fun getExerciseSets(dayAndWorkout: DayAndWorkout): List<NetworkExerciseSet> {
    val (workoutDay, workoutId) = dayAndWorkout
    return withContext(Dispatchers.IO) {
      val db = getDb()

      val keyValues = MutableExerciseSet(workoutId)
      log.i(
        TAG,
        "Sending query request to load day $workoutDay from workout $workoutId"
      )

      val dynamoSets =
        db.queryReverseIndex(
          MutableExerciseSet::class.java,
          keyValues,
          "$workoutDay.",
          ComparisonOperator.BEGINS_WITH
        )
      log.i(TAG, "Query results received for day $workoutDay from workout $workoutId")
      val exerciseSets = dynamoSets.map { set ->
        try {
          log.i(TAG, "loading exercise ${set.workout}: ${set.name} from dynamo")
          val mutableExercise =
            db.queryReverseIndex(
              MutableExercise::class.java,
              MutableExercise(workout = set.workout),
              set.name
            ).firstOrNull()
          val exercise = mutableExercise?.let { Exercise(it) } ?: Exercise(set.workout, set.name)
          mutableExercise ?: log.w(TAG, "Exercise not found")
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