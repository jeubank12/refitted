package com.litus_animae.refitted.data.dynamo

import android.content.Context
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression
import com.google.firebase.auth.FirebaseAuth
import com.litus_animae.refitted.data.dynamo.DynamoUtil.queryReverseIndex
import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.models.WorkoutPlan
import com.litus_animae.refitted.models.dynamo.DynamoGroupDefinition
import com.litus_animae.refitted.models.dynamo.DynamoWorkoutDay
import com.litus_animae.refitted.models.dynamo.DynamoWorkoutPlan
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DynamoWorkoutPlanNetworkService @Inject constructor(
  @ApplicationContext context: Context,
  log: LogUtil
) :
  DynamoNetworkService(context, log), WorkoutPlanNetworkService {
  private fun queryAllWorkoutPlans(db: DynamoDBMapper): Iterable<DynamoWorkoutPlan> {
    val keyValues = DynamoWorkoutPlan()
    val queryExpression = DynamoDBQueryExpression<DynamoWorkoutPlan>()
      .withHashKeyValues(keyValues)
      .withConsistentRead(false)
    return db.query(DynamoWorkoutPlan::class.java, queryExpression)
  }

  private fun querySpecificWorkoutPlans(
    db: DynamoDBMapper,
    planNames: Iterable<String>
  ): Iterable<DynamoWorkoutPlan> {
    return Iterable {
      planNames.asSequence().map { planName ->
        val keyValues = DynamoWorkoutPlan(planName)
        db.queryReverseIndex(DynamoWorkoutPlan::class.java, keyValues, "Plan")
      }.flatten().iterator()
    }
  }

  override suspend fun getWorkoutPlans(): List<WorkoutPlan> {
    return withContext(Dispatchers.IO) {
      val db = getDb()

      val currentUser = FirebaseAuth.getInstance().currentUser
        ?: throw IllegalStateException("Firebase user not logged in")
      val idToken = currentUser.getIdToken(false).await()
      val group = idToken.claims["group"]?.toString()

      val command = group?.let { groupValue ->
        val groupDefinition = db.load(DynamoGroupDefinition::class.java, groupValue, "Groups")
        querySpecificWorkoutPlans(db, groupDefinition.workouts)
      } ?: queryAllWorkoutPlans(db)

      command
        .mapNotNull { plan ->
          plan.workout?.let { workout ->
            val subKeyValues = DynamoWorkoutDay(workout = workout)
            val subQueryExpression = DynamoDBQueryExpression<DynamoWorkoutDay>()
              .withHashKeyValues(subKeyValues)
              .withIndexName("Reverse-index")
              .withFilterExpression("attribute_exists(Exercises)")
              .withConsistentRead(false)
            val days = db.query(DynamoWorkoutDay::class.java, subQueryExpression)
            log.d(TAG, "Got days for workout $workout: ${days.map { it.day }}")
            val totalDays = days.maxOfOrNull { it.day?.toIntOrNull() ?: 0 }
            val restDays = days.filter {
              log.d(TAG, "Got day $it")
              it.exercises.contains("0")
            }
              .mapNotNull { it.day?.toIntOrNull() ?: 0 }
            log.d(TAG, "Got rest days for workout $workout: $restDays")
            val alternateLabels = plan.globalAlternateLabels.split(';')
              .filter { it.isNotBlank() }
            if (totalDays != null) {
              WorkoutPlan(
                workout,
                totalDays,
                restDays = restDays,
                description = plan.description,
                globalAlternateLabels = alternateLabels,
                globalAlternate = if (alternateLabels.isNotEmpty()) 0 else null
              )
            } else WorkoutPlan(
              workout,
              restDays = restDays,
              description = plan.description,
              globalAlternateLabels = alternateLabels
            )
          }
        }
    }
  }

  companion object {
    private const val TAG = "DynamoNetworkService"
  }
}