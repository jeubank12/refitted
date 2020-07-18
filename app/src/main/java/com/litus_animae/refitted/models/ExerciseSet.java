package com.litus_animae.refitted.models;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;

@Entity(primaryKeys = {"day", "step", "workout"},
        foreignKeys = @ForeignKey(entity = Exercise.class,
                parentColumns = {"exercise_name", "exercise_workout"},
                childColumns = {"name", "workout"}),
        indices = @Index({"name", "workout"}))
@DynamoDBTable(tableName = "refitted-exercise")
public class ExerciseSet {
    @NonNull
    private String workout = "";
    private String id = "0.0";
    private String note;
    private String name;
    @NonNull
    private String day = "0";
    @NonNull
    private String step = "0";
    private int reps;
    private int sets;
    private boolean toFailure;
    private int rest;
    private int repsRange = 0;
    private String repsUnit;
    @Ignore
    private LiveData<Exercise> exercise;
    @Ignore
    private ExerciseSet alternate;
    @Ignore
    private boolean isActive = true;

    public ExerciseSet() {
    }

    @DynamoDBHashKey(attributeName = "Id")
    @DynamoDBAttribute(attributeName = "Id")
    @DynamoDBIndexRangeKey(attributeName = "Id", globalSecondaryIndexName = "Reverse-index")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDay() {
        return id.split("\\.", 2)[0];
    }
    public void setDay(String day){ id = day + "." + getStep(); }

    public String getStep() {
        return id.split("\\.", 2)[1];
    }
    public void setStep(String step){ id = getDay() + "." + step; }

    @DynamoDBRangeKey(attributeName = "Disc")
    @DynamoDBAttribute(attributeName = "Disc")
    @DynamoDBIndexHashKey(attributeName = "Disc", globalSecondaryIndexName = "Reverse-index")
    public String getWorkout() {
        return workout;
    }

    public void setWorkout(String workout) {
        this.workout = workout;
    }

    @DynamoDBAttribute(attributeName = "Name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExerciseName() {
        return name == null || name.isEmpty() || !name.contains("_") ? "" :
                name.split("_", 2)[1];
    }

    @DynamoDBAttribute(attributeName = "Note")
    public String getNote() {
        return note == null ? "" : note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @DynamoDBAttribute(attributeName = "Reps")
    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    @DynamoDBAttribute(attributeName = "Sets")
    public int getSets() {
        return sets;
    }

    public void setSets(int sets) {
        this.sets = sets;
    }

    @DynamoDBAttribute(attributeName = "ToFailure")
    public boolean isToFailure() {
        return toFailure;
    }

    public void setToFailure(boolean toFailure) {
        this.toFailure = toFailure;
    }

    public LiveData<Exercise> getExercise() {
        return exercise;
    }

    public void setExercise(LiveData<Exercise> exercise) {
        this.exercise = exercise;
    }

    @DynamoDBAttribute(attributeName = "Rest")
    public int getRest() {
        return rest;
    }

    public void setRest(int rest) {
        this.rest = rest;
    }

    public ExerciseSet getAlternate() {
        return alternate;
    }

    public void setAlternate(ExerciseSet alternate) {
        this.alternate = alternate;
    }

    public boolean hasAlternate() {
        return alternate != null;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    @DynamoDBAttribute(attributeName = "RepsRange")
    public int getRepsRange() {
        return repsRange;
    }

    public void setRepsRange(int repsRange) {
        this.repsRange = repsRange;
    }

    @DynamoDBAttribute(attributeName = "RepsUnit")
    public String getRepsUnit() {
        return repsUnit == null ? "" : repsUnit;
    }

    public void setRepsUnit(String repsUnit) {
        this.repsUnit = repsUnit;
    }
}
