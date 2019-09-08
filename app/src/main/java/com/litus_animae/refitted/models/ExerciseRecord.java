package com.litus_animae.refitted.models;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.DataSource;

import java.util.List;

public class ExerciseRecord {
    private ExerciseSet targetSet;

    private LiveData<SetRecord> latestSet;

    private DataSource.Factory<Integer, SetRecord> allSets;

    private LiveData<List<SetRecord>> sets;

    public ExerciseRecord(ExerciseSet targetSet){
        this.targetSet = targetSet;
    }

    public LiveData<SetRecord> getSet(int set) {
        return Transformations.map(getSets(), sets -> {
            if (set < sets.size() && set >= 0) {
                return sets.get(set);
            } else if (set < 0 && sets.size() + set >= 0){
                return sets.get(sets.size() + set);
            }
            return null;
        });
    }

    public LiveData<Integer> getSetsCount(){
        return Transformations.map(getSets(), sets -> {
            if (sets != null){
                return sets.size();
            }
            return 0;
        });
    }

    public ExerciseSet getTargetSet() {
        return targetSet;
    }

    public LiveData<List<SetRecord>> getSets() {
        return sets;
    }

    public void setSets(LiveData<List<SetRecord>> sets) {
        this.sets = sets;
    }

    public LiveData<SetRecord> getLatestSet() {
        return latestSet;
    }

    public void setLatestSet(LiveData<SetRecord> latestSet) {
        this.latestSet = latestSet;
    }

    public DataSource.Factory<Integer, SetRecord> getAllSets() {
        return allSets;
    }

    public void setAllSets(DataSource.Factory<Integer, SetRecord> allSets) {
        this.allSets = allSets;
    }
}
