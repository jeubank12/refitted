package com.litus_animae.refitted.models.dynamo

import androidx.room.PrimaryKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@DynamoDBTable(tableName = "refitted-exercise")
data class DynamoWorkoutPlan @JvmOverloads constructor(
  @get:DynamoDBIndexHashKey(attributeName = "Disc", globalSecondaryIndexName = "Reverse-index")
  @get:DynamoDBAttribute(attributeName = "Disc")
  @get:DynamoDBRangeKey(attributeName = "Disc")
  @PrimaryKey
  var workout: String?,

  @get:DynamoDBIndexRangeKey(attributeName = "Id", globalSecondaryIndexName = "Reverse-index")
  @get:DynamoDBAttribute(attributeName = "Id")
  @get:DynamoDBHashKey(attributeName = "Id")
  var id: String = "Plan",

  @get:DynamoDBAttribute(attributeName = "Description")
  var description: String = "",

  @get:DynamoDBAttribute(attributeName = "GlobalAlternateLabels")
  var globalAlternateLabels: String = ""
) {

  constructor() : this(null)
}