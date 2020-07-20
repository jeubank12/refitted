package com.litus_animae.refitted.models

import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@DynamoDBTable(tableName = "refitted-exercise")
data class MutableExercise @JvmOverloads constructor(
        @get:DynamoDBAttribute(attributeName = "Disc")
        @get:DynamoDBRangeKey(attributeName = "Disc")
        var workout: String = "",

        @get:DynamoDBAttribute(attributeName = "Id")
        @get:DynamoDBHashKey(attributeName = "Id")
        var id: String = "",

        @get:DynamoDBAttribute(attributeName = "Note")
        var description: String? = null
)