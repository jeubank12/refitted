package com.litus_animae.refitted.data.dynamo

import android.content.Context
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig.TableNameOverride
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.google.firebase.auth.FirebaseAuth
import com.litus_animae.refitted.R
import com.litus_animae.refitted.util.LogUtil
import com.litus_animae.refitted.util.exception.UserNotLoggedInException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant

@ExperimentalCoroutinesApi
@FlowPreview
abstract class DynamoNetworkService(context: Context, protected val log: LogUtil) {
  private val applicationContext = context.applicationContext
  private val credentialsProvider: CognitoCachingCredentialsProvider by lazy {
    CognitoCachingCredentialsProvider(
      applicationContext,
      applicationContext.getString(R.string.cognito_identity_pool_id),
      Regions.US_EAST_2
    )
  }
  private val tableName: String = applicationContext.getString(R.string.dynamo_table)
  private val openIdSource: String = applicationContext.getString(R.string.firebase_id_source)

  protected suspend fun getDb(): DynamoDBMapper {
    val currentUser = FirebaseAuth.getInstance().currentUser
      ?: throw UserNotLoggedInException("Firebase user not logged in")
    val idToken = currentUser.getIdToken(false).await()
    val logins = mapOf(Pair(openIdSource, idToken.token))
    credentialsProvider.logins = logins
    val start = Instant.now()
    val dbClient = AmazonDynamoDBClient(credentialsProvider)
    dbClient.setRegion(Region.getRegion(Regions.US_EAST_2))
    log.d(TAG, "GetDatabaseMapper: generating mapper to table: $tableName")
    val dynamoDb = DynamoDBMapper.builder()
      .dynamoDBClient(dbClient)
      .dynamoDBMapperConfig(
        DynamoDBMapperConfig(
          TableNameOverride(tableName)
        )
      )
      .build()
    val endOpen = Instant.now()
    log.d(
      TAG,
      "connectToDynamo: Dynamo took " + Duration.between(
        start,
        endOpen
      ) + " to open. Started at: " + start.toString()
    )
    return dynamoDb
  }

  companion object {
    private const val TAG = "DynamoNetworkService"
  }

}