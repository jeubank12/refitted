package com.litus_animae.refitted.data

import androidx.lifecycle.LiveData
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord

interface ExerciseRepository {
    fun loadExercises(day: String, workoutId: String)
    fun storeSetRecord(record: SetRecord)
    val exercises: LiveData<List<ExerciseSet>>
    val records: LiveData<List<ExerciseRecord>>
    fun shutdown()
}