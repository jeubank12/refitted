package com.litus_animae.refitted.dynamo.entities

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import com.litus_animae.refitted.data.models.Exercise

/**
 * DynamoDB entity for Exercise persistence.
 * Domain code should use the corresponding model from :data instead.
 */
@DynamoDBTable(tableName = "refitted-exercise")
internal data class DynamoExercise @JvmOverloads constructor(
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
) {
    /**
     * Convert DynamoDB entity to domain model
     */
    fun toDomain(): Exercise = Exercise(
        workout = workout,
        id = id,
        description = description
    )
}
