package com.litus_animae.refitted.data

import com.litus_animae.refitted.data.models.ExerciseCompletionRecord
import com.litus_animae.refitted.data.models.ExerciseRecord
import com.litus_animae.refitted.data.models.ExerciseSet
import com.litus_animae.refitted.data.models.SetRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for exercise-related operations.
 * Abstracts data access for exercises, sets, and records.
 */
interface ExerciseRepository {
    suspend fun loadExercises(day: String, workoutId: String)
    fun refreshExercises()
    suspend fun storeSetRecord(record: SetRecord)
    fun loadWorkoutRecords(workoutId: String)
    val exercises: Flow<List<ExerciseSet>>
    val exercisesAreLoading: StateFlow<Boolean>
    val records: Flow<List<ExerciseRecord>>
    val workoutRecords: Flow<List<ExerciseCompletionRecord>>
}
