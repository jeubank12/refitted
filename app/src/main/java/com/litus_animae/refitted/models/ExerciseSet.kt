package com.litus_animae.refitted.models

import androidx.annotation.NonNull
import androidx.lifecycle.LiveData
import androidx.room.*
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*
import com.litus_animae.refitted.models.Exercise

// TODO generate migration
@Entity(primaryKeys = ["day", "step", "workout"],
        foreignKeys = [ForeignKey(entity = Exercise::class,
                parentColumns = ["exercise_name", "exercise_workout"],
                childColumns = ["name", "workout"])],
        indices = [Index(value = ["name", "workout"])])
@DynamoDBTable(tableName = "refitted-exercise")
class ExerciseSet {
    @get:DynamoDBIndexHashKey(attributeName = "Disc", globalSecondaryIndexName = "Reverse-index")
    @get:DynamoDBAttribute(attributeName = "Disc")
    @get:DynamoDBRangeKey(attributeName = "Disc")
    var workout = ""

    @get:DynamoDBIndexRangeKey(attributeName = "Id", globalSecondaryIndexName = "Reverse-index")
    @get:DynamoDBAttribute(attributeName = "Id")
    @get:DynamoDBHashKey(attributeName = "Id")
    var id: String
        get() = "$day.$step"
        set(id) {
            val values = id.split("\\.".toRegex(), 2).toTypedArray()
            day = values[0]
            step = values[1]
        }

    @get:DynamoDBAttribute(attributeName = "Note")
    var note: String? = null
        get() = if (field == null) "" else field

    @get:DynamoDBAttribute(attributeName = "Name")
    var name: String? = null

    @NonNull
    var day: String? = null

    @NonNull
    var step: String? = null

    @get:DynamoDBAttribute(attributeName = "Reps")
    var reps = 0

    @get:DynamoDBAttribute(attributeName = "Sets")
    var sets = 0

    @get:DynamoDBAttribute(attributeName = "ToFailure")
    var isToFailure = false

    @get:DynamoDBAttribute(attributeName = "Rest")
    var rest = 0

    @get:DynamoDBAttribute(attributeName = "RepsRange")
    var repsRange = 0

    @get:DynamoDBAttribute(attributeName = "RepsUnit")
    var repsUnit: String? = null
        get() = if (field == null) "" else field

    @Ignore
    var exercise: LiveData<Exercise>? = null

    @Ignore
    var alternate: ExerciseSet? = null

    @Ignore
    var isActive = true

    val exerciseName: String
        get() = if (name == null || name!!.isEmpty() || !name!!.contains("_")) "" else name!!.split("_".toRegex(), 2).toTypedArray()[1]

    fun hasAlternate(): Boolean {
        return alternate != null
    }

}