package com.litus_animae.refitted.data

import androidx.lifecycle.LiveData
import arrow.fx.IO
import com.litus_animae.refitted.data.room.ExerciseDao
import com.litus_animae.refitted.models.ExerciseRecord
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.SetRecord

interface ExerciseRepository {
    fun loadExercises(day: String, workoutId: String): IO<Unit>
    fun storeSetRecord(record: SetRecord): IO<Unit>
    fun loadWorkoutRecords(workoutId: String): Unit
    val exercises: LiveData<List<ExerciseSet>>
    val records: LiveData<List<ExerciseRecord>>
    val workoutRecords: LiveData<List<ExerciseDao.ExerciseCompletionRecord>>
}