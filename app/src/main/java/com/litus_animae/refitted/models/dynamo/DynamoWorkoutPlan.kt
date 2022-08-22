package com.litus_animae.refitted.models.dynamo

import androidx.room.PrimaryKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@DynamoDBTable(tableName = "refitted-exercise")
data class DynamoWorkoutPlan @JvmOverloads constructor(
  @get:DynamoDBAttribute(attributeName = "Disc")
  @get:DynamoDBRangeKey(attributeName = "Disc")
  @PrimaryKey
  var workout: String?,

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