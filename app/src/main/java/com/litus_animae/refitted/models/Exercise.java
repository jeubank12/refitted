package com.litus_animae.refitted.models;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;

import java.io.Serializable;

@DynamoDBTable(tableName = "refitted-exercise")
public class Exercise implements Serializable {
    private String workout;
    private String id;
    private String description;

    @DynamoDBHashKey(attributeName = "Id")
    @DynamoDBAttribute(attributeName = "Id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DynamoDBRangeKey(attributeName = "Disc")
    @DynamoDBAttribute(attributeName = "Disc")
    public String getWorkout() {
        return workout;
    }

    public void setWorkout(String workout) {
        this.workout = workout;
    }

    public String getName() {
        return id.split("_", 2)[1];
    }

    public void setName(String name) {
        id = getCategory() + name;
    }

    public String getCategory() {
        return id.split("_", 2)[0];
    }

    public void setCategory(String category) {
        // TODO ensure no underscore in category
        id = category + getName();
    }

    @DynamoDBAttribute(attributeName = "Note")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
