package com.litus_animae.refitted.models

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@DynamoDBTable(tableName = "refitted-exercise")
class WorkoutPlan {
    @get:DynamoDBAttribute(attributeName = "Disc")
    @get:DynamoDBRangeKey(attributeName = "Disc")
    var workout: String? = null

    @get:DynamoDBAttribute(attributeName = "Id")
    @get:DynamoDBHashKey(attributeName = "Id")
    var id = "Plan"

}