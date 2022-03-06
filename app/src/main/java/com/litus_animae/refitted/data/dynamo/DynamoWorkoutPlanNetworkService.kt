package com.litus_animae.refitted.data.dynamo

import android.content.Context
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression
import com.litus_animae.refitted.data.network.WorkoutPlanNetworkService
import com.litus_animae.refitted.models.DynamoWorkoutPlan
import com.litus_animae.refitted.models.WorkoutPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DynamoWorkoutPlanNetworkService @Inject constructor(@ApplicationContext context: Context) :
    DynamoNetworkService(context), WorkoutPlanNetworkService {
    override suspend fun getWorkoutPlans(): List<WorkoutPlan> {
        return withContext(Dispatchers.IO) {
            val db = getDb()

            val keyValues = DynamoWorkoutPlan()
            val queryExpression = DynamoDBQueryExpression<DynamoWorkoutPlan>()
                .withHashKeyValues(keyValues)
                .withConsistentRead(false)
            db.query(DynamoWorkoutPlan::class.java, queryExpression)
                .mapNotNull { plan -> plan.workout?.let { WorkoutPlan(it) } }
        }
    }
}