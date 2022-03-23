package com.litus_animae.refitted.data

import com.litus_animae.refitted.data.room.ExerciseDao
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ExerciseRepository {
    suspend fun loadExercises(day: String, workoutId: String)
    suspend fun storeSetRecord(record: SetRecord)
    fun loadWorkoutRecords(workoutId: String)
    val exercises: Flow<List<ExerciseSet>>
    val exercisesAreLoading: StateFlow<Boolean>
    val records: Flow<List<ExerciseRecord>>
    val workoutRecords: Flow<List<ExerciseDao.ExerciseCompletionRecord>>
}