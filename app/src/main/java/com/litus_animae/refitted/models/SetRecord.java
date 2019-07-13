package com.litus_animae.refitted.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.time.Instant;
import java.util.Date;

public class SetRecord implements Parcelable {
    private double weight;
    private int reps;
    private Date completed;

    public SetRecord(double weight, int reps) {
        this.weight = weight;
        this.reps = reps;
        this.completed = Date.from(Instant.now());
    }

    protected SetRecord(Parcel in) {
        weight = in.readDouble();
        reps = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(weight);
        dest.writeInt(reps);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SetRecord> CREATOR = new Creator<SetRecord>() {
        @Override
        public SetRecord createFromParcel(Parcel in) {
            return new SetRecord(in);
        }

        @Override
        public SetRecord[] newArray(int size) {
            return new SetRecord[size];
        }
    };

    public double getWeight() {
        return weight;
    }

    public int getReps() {
        return reps;
    }

    public Date getCompleted() {
        return completed;
    }
}
