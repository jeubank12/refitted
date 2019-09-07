package com.litus_animae.refitted.models;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class ExerciseRecord {
    private ExerciseSet targetSet;

    private LiveData<SetRecord> latestSet = new MutableLiveData<>();

    private LiveData<List<SetRecord>> allSets = new MutableLiveData<>();

    private LiveData<List<SetRecord>> sets = new MutableLiveData<>();

    public ExerciseRecord(ExerciseSet targetSet){
        this.targetSet = targetSet;
        MutableLiveData<SetRecord> setLatest = new MutableLiveData<>();
        setLatest.setValue(new SetRecord());
        latestSet = setLatest;

        MutableLiveData<List<SetRecord>> emptySets = new MutableLiveData<>();
        emptySets.setValue(new ArrayList<>());
        allSets = emptySets;
        sets = emptySets;
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
        List<SetRecord> sets = getSets();
        if (sets != null){
            return sets.size();
        }
        return 0;
    }

    public ExerciseSet getTargetSet() {
        return targetSet;
    }

    public List<SetRecord> getSets() {
        return sets.getValue();
    }

    public void setSets(LiveData<List<SetRecord>> sets) {
        this.sets = sets;
    }

    public SetRecord getLatestSet() {
        return latestSet.getValue();
    }

    public void setLatestSet(LiveData<SetRecord> latestSet) {
        this.latestSet = latestSet;
    }

    public List<SetRecord> getAllSets() {
        return allSets.getValue();
    }

    public void setAllSets(LiveData<List<SetRecord>> allSets) {
        this.allSets = allSets;
    }
}
