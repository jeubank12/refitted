package com.litus_animae.refitted.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;

@Entity(primaryKeys = {"day", "step", "workout"},
        foreignKeys = @ForeignKey(entity = Exercise.class,
                parentColumns = {"exercise_name", "exercise_workout"},
                childColumns = {"name", "workout"}),
        indices = @Index({"name", "workout"}))
@DynamoDBTable(tableName = "refitted-exercise")
public class ExerciseSet implements Parcelable {
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
    @Ignore
    private Exercise exercise;
    @Ignore
    private ExerciseSet alternate;
    @Ignore
    private boolean isActive = true;

    public ExerciseSet() {
    }

    @DynamoDBHashKey(attributeName = "Id")
    @DynamoDBAttribute(attributeName = "Id")
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

    @DynamoDBAttribute(attributeName = "Note")
    public String getNote() {
        return note;
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

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @DynamoDBAttribute(attributeName = "Rest")
    public int getRest() {
        return rest;
    }

    public void setRest(int rest) {
        this.rest = rest;
    }

    protected ExerciseSet(Parcel in) {
        workout = in.readString();
        id = in.readString();
        note = in.readString();
        name = in.readString();
        reps = in.readInt();
        sets = in.readInt();
        toFailure = in.readByte() != 0;
        rest = in.readInt();
        exercise = in.readParcelable(Exercise.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(workout);
        dest.writeString(id);
        dest.writeString(note);
        dest.writeString(name);
        dest.writeInt(reps);
        dest.writeInt(sets);
        dest.writeByte((byte) (toFailure ? 1 : 0));
        dest.writeInt(rest);
        dest.writeParcelable(exercise, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ExerciseSet> CREATOR = new Creator<ExerciseSet>() {
        @Override
        public ExerciseSet createFromParcel(Parcel in) {
            return new ExerciseSet(in);
        }

        @Override
        public ExerciseSet[] newArray(int size) {
            return new ExerciseSet[size];
        }
    };

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
}
