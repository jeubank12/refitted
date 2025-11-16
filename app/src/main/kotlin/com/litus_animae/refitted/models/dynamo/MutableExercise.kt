package com.litus_animae.refitted.models.dynamo

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@DynamoDBTable(tableName = "refitted-exercise")
data class MutableExercise @JvmOverloads constructor(
  @get:DynamoDBIndexHashKey(attributeName = "Disc", globalSecondaryIndexName = "Reverse-index")
  @get:DynamoDBAttribute(attributeName = "Disc")
  @get:DynamoDBRangeKey(attributeName = "Disc")
  var workout: String = "",

  @get:DynamoDBIndexRangeKey(attributeName = "Id", globalSecondaryIndexName = "Reverse-index")
  @get:DynamoDBAttribute(attributeName = "Id")
  @get:DynamoDBHashKey(attributeName = "Id")
  var id: String = "",

  @get:DynamoDBAttribute(attributeName = "Note")
  var description: String? = null
)