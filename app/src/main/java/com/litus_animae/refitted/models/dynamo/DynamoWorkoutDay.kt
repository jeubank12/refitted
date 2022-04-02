package com.litus_animae.refitted.models.dynamo

import androidx.room.PrimaryKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*

@DynamoDBTable(tableName = "refitted-exercise")
data class DynamoWorkoutDay @JvmOverloads constructor(
  @get:DynamoDBIndexHashKey(attributeName = "Disc", globalSecondaryIndexName = "Reverse-index")
  @get:DynamoDBAttribute(attributeName = "Disc")
  @get:DynamoDBRangeKey(attributeName = "Disc")
  @PrimaryKey
  var workout: String? = null,

  @get:DynamoDBIndexRangeKey(attributeName = "Id", globalSecondaryIndexName = "Reverse-index")
  @get:DynamoDBAttribute(attributeName = "Id")
  @get:DynamoDBHashKey(attributeName = "Id")
  var day: String? = null,

  @get:DynamoDBAttribute(attributeName = "Exercises")
  var exercises: Set<String> = emptySet()
)