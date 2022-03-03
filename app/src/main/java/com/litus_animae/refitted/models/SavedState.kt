package com.litus_animae.refitted.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@Entity(tableName = "SavedState")
data class SavedState(
    @PrimaryKey
    val key: String,
    val value: String
)