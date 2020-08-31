package com.litus_animae.refitted.data.dynamo.coroutine

import android.content.Context
import android.util.Log
import arrow.fx.IO
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

abstract class DynamoDataService internal constructor(applicationContext: Context, protected val room: ExerciseRoom) {
    private val credentialsProvider: CognitoCachingCredentialsProvider = CognitoCachingCredentialsProvider(
            applicationContext,
            applicationContext.getString(R.string.cognito_identity_pool_id),
            Regions.US_EAST_2
    )
    private val tableName: String = applicationContext.getString(R.string.dynamo_table)
    var dynamoDb: DynamoDBMapper? = null
    private val openIdSource: String = applicationContext.getString(R.string.firebase_id_source)
    protected fun connectAndRun(): IO<Unit> {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return IO.lazy
        val idTokenIO = IO.effect { currentUser.getIdToken(false).await() }
        return idTokenIO.map {
            res -> res.token }.map {
            token -> mapOf(Pair(openIdSource, token)) }.flatMap {
            logins -> connectToDynamo(logins) }
    }

    private fun connectToDynamo(logins: Map<String, String?>): IO<Unit> {
        credentialsProvider.logins = logins
        val start = Instant.now()
        val dbClient = AmazonDynamoDBClient(credentialsProvider)
        dbClient.setRegion(Region.getRegion(Regions.US_EAST_2))
        Log.d(TAG, "GetDatabaseMapper: generating mapper to table: $tableName")
        dynamoDb = DynamoDBMapper.builder()
                .dynamoDBClient(dbClient)
                .dynamoDBMapperConfig(DynamoDBMapperConfig(
                        TableNameOverride(tableName)))
                .build()
        val endOpen = Instant.now()
        Log.d(TAG, "connectToDynamo: Dynamo took " + Duration.between(start, endOpen) + " to open. Started at: " + start.toString())
        return runAfterConnect().followedBy(IO.just(logEndQuery(endOpen)))
    }

    private fun logEndQuery(endOpen: Instant) {
        val endQuery = Instant.now()
        Log.d(TAG, "connectToDynamo: Dynamo query took " + Duration.between(endOpen, endQuery) + ". Started at: " + endOpen.toString())
    }

    protected abstract fun runAfterConnect(): IO<Unit>

    companion object {
        private const val TAG = "DynamoExerciseDataService"
    }

}