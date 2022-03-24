package com.litus_animae.refitted.models.dynamo

import androidx.room.ColumnInfo
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*

@DynamoDBTable(tableName = "refitted-exercise")
data class MutableExerciseSet @JvmOverloads constructor(
    @get:DynamoDBIndexHashKey(attributeName = "Disc", globalSecondaryIndexName = "Reverse-index")
    @get:DynamoDBAttribute(attributeName = "Disc")
    @get:DynamoDBRangeKey(attributeName = "Disc")
    var workout: String = "",

    @get:DynamoDBIndexRangeKey(attributeName = "Id", globalSecondaryIndexName = "Reverse-index")
    @get:DynamoDBAttribute(attributeName = "Id")
    @get:DynamoDBHashKey(attributeName = "Id")
    var id: String = "",

    @get:DynamoDBAttribute(attributeName = "Name")
    var name: String = "",

    @get:DynamoDBAttribute(attributeName = "Note")
    var note: String = "",

    @get:DynamoDBAttribute(attributeName = "Reps")
    var reps: Int = 0,

    @get:DynamoDBAttribute(attributeName = "Sets")
    var sets: Int = 0,

    @get:DynamoDBAttribute(attributeName = "ToFailure")
    @ColumnInfo(name = "toFailure")
    var isToFailure: Boolean = false,

    @get:DynamoDBAttribute(attributeName = "Rest")
    var rest: Int = 0,

    @get:DynamoDBAttribute(attributeName = "RepsUnit")
    var repsUnit: String = "",

    @get:DynamoDBAttribute(attributeName = "RepsRange")
    var repsRange: Int = 0
)