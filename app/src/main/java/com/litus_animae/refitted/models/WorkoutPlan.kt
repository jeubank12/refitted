package com.litus_animae.refitted.models

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@DynamoDBTable(tableName = "refitted-exercise")
class WorkoutPlan {
    @JvmOverloads constructor(
            workout: String?,
            id: String = "Plan"
    ){
        this.workout = workout
        this.id = id
    }

    constructor(): this(null)


    @get:DynamoDBAttribute(attributeName = "Disc")
    @get:DynamoDBRangeKey(attributeName = "Disc")
    val workout: String?

    @get:DynamoDBAttribute(attributeName = "Id")
    @get:DynamoDBHashKey(attributeName = "Id")
    val id: String
}