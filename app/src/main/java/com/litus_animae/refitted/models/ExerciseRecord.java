package com.litus_animae.refitted.models;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.List;

public class ExerciseRecord {
    @Embedded
    private ExerciseSet targetSet;

    private SetRecord latestSet;

    private List<SetRecord> allSets;

    @Relation(parentColumn = "name", entityColumn = "exercise", entity = SetRecord.class)
    private List<SetRecord> sets = new ArrayList<>();

    public ExerciseRecord(ExerciseSet targetSet){
        this.targetSet = targetSet;
    }

    public void addSet(SetRecord record) {
        getSets().add(record);
    }

    public SetRecord getSet(int set) {
        if (set < getSets().size() && set >= 0) {
            return getSets().get(set);
        } else if (set < 0 && getSets().size() + set >= 0){
            return getSets().get(getSets().size() + set);
        }
        return null;
    }

    public int getSetsCount(){
        return getSets().size();
    }

    public ExerciseSet getTargetSet() {
        return targetSet;
    }

    public void setTargetSet(ExerciseSet targetSet) {
        this.targetSet = targetSet;
    }

    public List<SetRecord> getSets() {
        return sets;
    }

    public void setSets(List<SetRecord> sets) {
        this.sets = sets;
    }

    public SetRecord getLatestSet() {
        return latestSet;
    }

    public void setLatestSet(SetRecord latestSet) {
        this.latestSet = latestSet;
    }

    public List<SetRecord> getAllSets() {
        return allSets;
    }

    public void setAllSets(List<SetRecord> allSets) {
        this.allSets = allSets;
    }
}
