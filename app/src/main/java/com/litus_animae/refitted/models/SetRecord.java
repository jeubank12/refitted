package com.litus_animae.refitted.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;

import java.time.Instant;
import java.util.Date;

@Entity(primaryKeys = {"exercise", "completed"})
public class SetRecord {
    private double weight;

    private int reps;
    private String workout;
    private String target_set;
    @NonNull
    private Date completed;
    @NonNull
    private String exercise = "";

    public SetRecord(ExerciseSet targetSet, double weight, int reps) {
        this.weight = weight;
        this.reps = reps;
        this.completed = Date.from(Instant.now());
        this.exercise = targetSet.getExerciseName();
        this.target_set = targetSet.getId();
        this.workout = targetSet.getWorkout();
    }

    public SetRecord(){
        this.completed = Date.from(Instant.now());
    }

    public String getExercise() {
        return exercise;
    }

    public void setExercise(String exercise) {
        this.exercise = exercise;
    }

    public Date getCompleted() {
        return completed;
    }

    public void setCompleted(Date completed) {
        this.completed = completed;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public String getWorkout() {
        return workout;
    }

    public void setWorkout(String workout) {
        this.workout = workout;
    }

    public String getTarget_set() {
        return target_set;
    }

    public void setTarget_set(String target_set) {
        this.target_set = target_set;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        SetRecord record = (SetRecord) obj;
        if (record != null){
            return record.workout.equals(workout)
                    && record.target_set.equals(target_set)
                    && record.reps == reps
                    && record.weight == weight
                    && record.completed.equals(completed);
        }
        return false;
    }
}
