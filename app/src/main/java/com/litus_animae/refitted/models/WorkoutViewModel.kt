package com.litus_animae.refitted.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val log: LogUtil
) : ViewModel() {
    val completedDays: LiveData<Map<Int, Date>> =
        exerciseRepo.workoutRecords.map { records ->
            records.groupBy { it.day }
                .mapValues { entry -> entry.value.maxOf { it.latestCompletion } }
                .mapKeys { it.key.toIntOrNull() ?: 0 }
        }.asLiveData(viewModelScope.coroutineContext)

    fun loadWorkoutDaysCompleted(workoutId: String) {
        exerciseRepo.loadWorkoutRecords(workoutId)
    }
}