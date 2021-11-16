package com.litus_animae.refitted.data.dynamo

import android.content.Context
import android.util.Log
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig.TableNameOverride
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.google.firebase.auth.FirebaseAuth
import com.litus_animae.refitted.R
import com.litus_animae.refitted.data.room.ExerciseRoom
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant

abstract class DynamoDataService internal constructor(
    applicationContext: Context,
    protected val room: ExerciseRoom
) {
    private val credentialsProvider: CognitoCachingCredentialsProvider =
        CognitoCachingCredentialsProvider(
            applicationContext,
            applicationContext.getString(R.string.cognito_identity_pool_id),
            Regions.US_EAST_2
        )
    private val tableName: String = applicationContext.getString(R.string.dynamo_table)
    protected var dynamoDb: DynamoDBMapper? = null
    private val openIdSource: String = applicationContext.getString(R.string.firebase_id_source)
    protected suspend fun connectAndRun() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val idToken = currentUser.getIdToken(false).await()
        val logins = mapOf(Pair(openIdSource, idToken.token))
        connectToDynamo(logins)
    }

    private suspend fun connectToDynamo(logins: Map<String, String?>) {
        credentialsProvider.logins = logins
        val start = Instant.now()
        val dbClient = AmazonDynamoDBClient(credentialsProvider)
        dbClient.setRegion(Region.getRegion(Regions.US_EAST_2))
        Log.d(TAG, "GetDatabaseMapper: generating mapper to table: $tableName")
        dynamoDb = DynamoDBMapper.builder()
            .dynamoDBClient(dbClient)
            .dynamoDBMapperConfig(
                DynamoDBMapperConfig(
                    TableNameOverride(tableName)
                )
            )
            .build()
        val endOpen = Instant.now()
        Log.d(
            TAG,
            "connectToDynamo: Dynamo took " + Duration.between(
                start,
                endOpen
            ) + " to open. Started at: " + start.toString()
        )
        runAfterConnect()
        logEndQuery(endOpen)
    }

    private fun logEndQuery(endOpen: Instant) {
        val endQuery = Instant.now()
        Log.d(
            TAG,
            "connectToDynamo: Dynamo query took " + Duration.between(
                endOpen,
                endQuery
            ) + ". Started at: " + endOpen.toString()
        )
    }

    protected abstract suspend fun runAfterConnect(): Unit

    companion object {
        private const val TAG = "DynamoExerciseDataService"
    }

}