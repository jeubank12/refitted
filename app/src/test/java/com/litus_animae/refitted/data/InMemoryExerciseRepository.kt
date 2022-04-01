package com.litus_animae.refitted.data

import com.litus_animae.refitted.data.room.ExerciseDao
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryExerciseRepository(
    private val initialExercises: List<ExerciseSet> = emptyList(),
    private val initialRecords: List<ExerciseRecord> = emptyList()
) : ExerciseRepository {
    private val exerciseList = MutableStateFlow(initialExercises)
    private val recordList = MutableStateFlow(initialRecords)

    override suspend fun loadExercises(day: String, workoutId: String) {
        recordList.value = initialRecords
        exerciseList.value = initialExercises
    }

    override fun refreshExercises() {
        TODO("Not yet implemented")
    }

    override suspend fun storeSetRecord(record: SetRecord) {
        TODO("Not yet implemented")
    }

    override fun loadWorkoutRecords(workoutId: String) {
        TODO("Not yet implemented")
    }

    override val exercises: Flow<List<ExerciseSet>> = exerciseList
    override val exercisesAreLoading: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override val records: Flow<List<ExerciseRecord>> = recordList
    override val workoutRecords: Flow<List<ExerciseDao.ExerciseCompletionRecord>>
        get() = TODO("Not yet implemented")
}