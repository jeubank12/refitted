package com.litus_animae.refitted.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.List;

public class ExerciseRecord implements Parcelable {
    @Embedded
    private ExerciseSet targetSet;

    @Relation(parentColumn = "name", entityColumn = "target_set", entity = SetRecord.class)
    private List<SetRecord> sets = new ArrayList<>();

    public ExerciseRecord(ExerciseSet targetSet){
        this.targetSet = targetSet;
    }

    protected ExerciseRecord(Parcel in) {
        targetSet = in.readParcelable(ExerciseSet.class.getClassLoader());
        sets = in.createTypedArrayList(SetRecord.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(targetSet, flags);
        dest.writeTypedList(sets);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ExerciseRecord> CREATOR = new Creator<ExerciseRecord>() {
        @Override
        public ExerciseRecord createFromParcel(Parcel in) {
            return new ExerciseRecord(in);
        }

        @Override
        public ExerciseRecord[] newArray(int size) {
            return new ExerciseRecord[size];
        }
    };

    public void addSet(SetRecord record) {
        sets.add(record);
    }

    public SetRecord getSet(int set) {
        if (set < sets.size() && set >= 0) {
            return sets.get(set);
        } else if (set < 0 && sets.size() + set >= 0){
            return sets.get(sets.size() + set);
        }
        return null;
    }

    public int getSetsCount(){
        return sets.size();
    }

    public ExerciseSet getTargetSet() {
        return targetSet;
    }

    public void setTargetSet(ExerciseSet targetSet) {
        this.targetSet = targetSet;
    }
}
