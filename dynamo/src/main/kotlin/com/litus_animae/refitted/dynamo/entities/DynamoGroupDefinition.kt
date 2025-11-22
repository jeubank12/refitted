package com.litus_animae.refitted.dynamo.entities

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

/**
 * DynamoDB entity for user group definitions.
 * Defines which workouts are accessible to different user groups (free, premium, etc.)
 */
@DynamoDBTable(tableName = "refitted-exercise")
internal data class DynamoGroupDefinition @JvmOverloads constructor(
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
