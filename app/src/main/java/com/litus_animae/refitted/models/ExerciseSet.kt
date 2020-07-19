package com.litus_animae.refitted.models

import androidx.annotation.NonNull
import androidx.lifecycle.LiveData
import androidx.room.*
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*
import com.litus_animae.refitted.models.Exercise

// TODO generate migration for day/step change
@Entity(primaryKeys = ["day", "step", "workout"],
        foreignKeys = [ForeignKey(entity = Exercise::class,
                parentColumns = ["exercise_name", "exercise_workout"],
                childColumns = ["name", "workout"])],
        indices = [Index(value = ["name", "workout"])])
@DynamoDBTable(tableName = "refitted-exercise")
data class ExerciseSet @JvmOverloads constructor(
    @get:DynamoDBIndexHashKey(attributeName = "Disc", globalSecondaryIndexName = "Reverse-index")
    @get:DynamoDBAttribute(attributeName = "Disc")
    @get:DynamoDBRangeKey(attributeName = "Disc")
    val workout: String = "",

    @get:DynamoDBIndexRangeKey(attributeName = "Id", globalSecondaryIndexName = "Reverse-index")
    @get:DynamoDBAttribute(attributeName = "Id")
    @get:DynamoDBHashKey(attributeName = "Id")
    val id: String = "",

    @get:DynamoDBAttribute(attributeName = "Name")
    val name: String = "",

    @get:DynamoDBAttribute(attributeName = "Note")
    val note: String = "",

    @get:DynamoDBAttribute(attributeName = "Reps")
    val reps: Int = 0,

    @get:DynamoDBAttribute(attributeName = "Sets")
    val sets: Int = 0,

    @get:DynamoDBAttribute(attributeName = "ToFailure")
    val isToFailure: Boolean = false,

    @get:DynamoDBAttribute(attributeName = "Rest")
    val rest: Int = 0,

    @get:DynamoDBAttribute(attributeName = "RepsUnit")
    val repsUnit: String = "",

    @get:DynamoDBAttribute(attributeName = "RepsRange")
    val repsRange: Int = 0) {

constructor(workout: String, day: String, step: String, name: String, note: String, reps: Int,
sets:Int, isToFailure: Boolean, rest: Int, repsUnit: String, repsRange: Int) : this(
        workout,
        "$day.$step",
name, note, reps, sets, isToFailure, rest, repsUnit, repsRange)

    @NonNull
    var day: String = id.split("\\.".toRegex(), 2).toTypedArray()[0]

    @NonNull
    var step: String = id.split("\\.".toRegex(), 2).toTypedArray()[1]

    @Ignore
    var exercise: LiveData<Exercise>? = null

    @Ignore
    var alternate: ExerciseSet? = null

    @Ignore
    var isActive = true

    val exerciseName: String
        get() = if (name.isEmpty() || !name.contains("_")) ""
        else name.split("_".toRegex(), 2).toTypedArray()[1]

    fun hasAlternate(): Boolean {
        return alternate != null
    }

}