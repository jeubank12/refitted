package com.litus_animae.refitted.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord

class InMemoryExerciseRepository(
        private val exercises: List<ExerciseSet> = emptyList(),
        private val records: List<ExerciseRecord> = emptyList()
) : ExerciseRepository {
    private val exerciseList = MutableLiveData<List<ExerciseSet>>()
    private val recordList = MutableLiveData<List<ExerciseRecord>>()
    override fun getRecords(): LiveData<List<ExerciseRecord>> {
        return recordList
    }

    override fun shutdown() {
        // no implementation
    }

    override fun storeSetRecord(record: SetRecord?) {
        TODO("Not yet implemented")
    }

    override fun getExercises(): LiveData<List<ExerciseSet>> {
        return exerciseList
    }

    override fun loadExercises(day: String, workoutId: String) {
        recordList.postValue(records)
        exerciseList.postValue(exercises)
    }
}