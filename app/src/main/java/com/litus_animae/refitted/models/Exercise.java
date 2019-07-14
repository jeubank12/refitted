package com.litus_animae.refitted.models;

import android.os.Parcel;
import android.os.Parcelable;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;

import java.io.Serializable;

@DynamoDBTable(tableName = "refitted-exercise")
public class Exercise implements Parcelable {
    private String workout;
    private String id;
    private String description;

    public Exercise(){}

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
        id = getCategory() + "_" + name;
    }

    public String getCategory() {
        return id.split("_", 2)[0];
    }

    public void setCategory(String category) {
        // TODO ensure no underscore in category
        id = category + "_" + getName();
    }

    @DynamoDBAttribute(attributeName = "Note")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(workout);
        dest.writeString(id);
        dest.writeString(description);
    }

    protected Exercise(Parcel in) {
        workout = in.readString();
        id = in.readString();
        description = in.readString();
    }

    public static final Creator<Exercise> CREATOR = new Creator<Exercise>() {
        @Override
        public Exercise createFromParcel(Parcel in) {
            return new Exercise(in);
        }

        @Override
        public Exercise[] newArray(int size) {
            return new Exercise[size];
        }
    };
}
