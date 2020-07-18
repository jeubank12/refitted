package com.litus_animae.refitted.models;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;

import java.util.Date;


@Entity(primaryKeys = {"exercise_name","exercise_workout"})
@DynamoDBTable(tableName = "refitted-exercise")
public class Exercise implements Parcelable {
    @NonNull
    @ColumnInfo(name = "exercise_workout")
    private String workout = "";
    @NonNull
    @ColumnInfo(name = "exercise_name")
    private String id = "";
    private String description;
    //private Date cacheDate;

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
        return id.isEmpty() || !id.contains("_") ? null :
                id.split("_", 2)[1];
    }

    public String getName(boolean allowNull) {
        if (allowNull){
            return getName();
        }
        return id.isEmpty() || !id.contains("_") ? "" :
                id.split("_", 2)[1];
    }

    public void setName(String name) {
        id = getCategory() + "_" + name;
    }

    public String getCategory() {
        return id.split("_", 2)[0];
    }

    public void setCategory(String category) {
        id = category.split("_", 2)[0] + "_" + getName(false);
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
