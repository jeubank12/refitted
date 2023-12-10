package com.litus_animae.refitted.models.dynamo

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@DynamoDBTable(tableName = "refitted-exercise")
data class DynamoGroupDefinition @JvmOverloads constructor(
  @get:DynamoDBAttribute(attributeName = "Id")
  @get:DynamoDBHashKey(attributeName = "Id")
  var groupId: String?,

  @get:DynamoDBAttribute(attributeName = "Disc")
  @get:DynamoDBRangeKey(attributeName = "Disc")
  var range: String = "Groups",

  @get:DynamoDBAttribute(attributeName = "Workouts")
  var workouts: Set<String> = emptySet(),
) {

  constructor() : this(null)
}