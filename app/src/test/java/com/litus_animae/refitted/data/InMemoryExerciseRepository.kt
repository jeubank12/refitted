package com.litus_animae.refitted.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import arrow.fx.IO
import arrow.fx.extensions.fx
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord

class InMemoryExerciseRepository(
    private val initialExercises: List<ExerciseSet> = emptyList(),
    private val initialRecords: List<ExerciseRecord> = emptyList()
) : ExerciseRepository {
    private val exerciseList = MutableLiveData<List<ExerciseSet>>()
    private val recordList = MutableLiveData<List<ExerciseRecord>>()

    override fun loadExercises(day: String, workoutId: String) =
        IO.fx {
            recordList.postValue(initialRecords)
            exerciseList.postValue(initialExercises)
        }

    override fun storeSetRecord(record: SetRecord): IO<Unit> {
        TODO("Not yet implemented")
    }

    override val exercises: LiveData<List<ExerciseSet>> = exerciseList
    override val records: LiveData<List<ExerciseRecord>> = recordList
}