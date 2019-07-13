package com.litus_animae.refitted.models;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;
import java.util.Set;

@DynamoDBTable(tableName = "refitted-exercise")
public class WorkoutDay {
    private String workout;
    private String day;
    private Set<String> exercises;

    @DynamoDBHashKey(attributeName = "Id")
    @DynamoDBAttribute(attributeName = "Id")
    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    @DynamoDBRangeKey(attributeName = "Disc")
    @DynamoDBAttribute(attributeName = "Disc")
    public String getWorkout() {
        return workout;
    }

    public void setWorkout(String workout) {
        this.workout = workout;
    }

    @DynamoDBAttribute(attributeName = "Exercises")
    public Set<String> getExercises() {
        return exercises;
    }

    public void setExercises(Set<String> exercises) {
        this.exercises = exercises;
    }
}
