package com.litus_animae.refitted.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable

@Entity(tableName = "workouts")
data class WorkoutPlan(
    @PrimaryKey
    val workout: String,
    val totalDays: Int = 84,
    val lastViewedDay: Int = 1
)

data class DayAndWorkout(val day: String, val workoutId: String)
