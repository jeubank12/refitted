package com.litus_animae.refitted.models

import androidx.lifecycle.ViewModel
import com.litus_animae.refitted.data.ExerciseRepository
import com.litus_animae.refitted.util.LogUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val log: LogUtil
) : ViewModel() {
    val completedDays: Flow<Map<Int, Date>> =
        exerciseRepo.workoutRecords.map { records ->
            records.groupBy { it.day }
                .mapValues { entry -> entry.value.maxOf { it.latestCompletion } }
                .mapKeys { it.key.toIntOrNull() ?: 0 }
        }

    private val maxCompletedDay = completedDays.map { it.keys.maxOrNull() }

    fun getIsDayCompleted(currentDay: Int) {
        maxCompletedDay.combine(completedDays) { maxDayKey, dayRecords ->
            val maxDay = maxDayKey ?: currentDay
            val maxDayCompletionDate =
                dayRecords.getOrDefault(maxDay, Date(1L))
            val currentDayCompletionDate = dayRecords.getOrDefault(currentDay, Date(0L))
            currentDay == maxDay || currentDayCompletionDate.after(maxDayCompletionDate)
        }
    }

    fun loadWorkoutDaysCompleted(workoutId: String) {
        exerciseRepo.loadWorkoutRecords(workoutId)
    }
}